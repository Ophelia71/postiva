package com.postiva.billing;

import com.postiva.usage.UsageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class BillingStore {
    private final JdbcTemplate jdbc;
    private final BillingProperties properties;
    private final UsageProperties usage;

    public BillingStore(JdbcTemplate jdbc, BillingProperties properties, UsageProperties usage) {
        this.jdbc = jdbc; this.properties = properties; this.usage = usage;
    }

    public static final String PAYOS = "PAYOS";
    public static final String BANK_TRANSFER = "BANK_TRANSFER";
    /** A bank transfer can take a while (another bank, a weekend): keep the order open for three days. */
    private static final long BANK_TRANSFER_SECONDS = 3 * 24 * 3600;

    @Transactional
    public Order draft(UUID userId, UUID key) { return draft(userId, key, PAYOS); }

    @Transactional
    public Order draft(UUID userId, UUID key, String provider) {
        var users = jdbc.queryForList("SELECT role, status FROM users WHERE id = ? FOR UPDATE", userId);
        if (users.isEmpty() || !"USER".equals(users.getFirst().get("role"))) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng");
        if (!"ACTIVE".equals(users.getFirst().get("status"))) throw error(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
        var previous = jdbc.query("SELECT * FROM payment_orders WHERE user_id = ? AND idempotency_key = ?",
                (rs, row) -> order(rs), userId, key);
        if (!previous.isEmpty()) return previous.getFirst();
        var open = jdbc.query("SELECT * FROM payment_orders WHERE user_id = ? AND status IN ('CREATING','PENDING') FOR UPDATE",
                (rs, row) -> order(rs), userId);
        if (!open.isEmpty()) {
            Order existing = open.getFirst();
            boolean live = existing.expiresAt().isAfter(Instant.now());
            if (live && existing.provider().equals(provider)) return existing;
            // Expired, or the shop switched how it takes payment: the old unpaid order would only confuse the buyer.
            jdbc.update("UPDATE payment_orders SET status = ?, updated_at = clock_timestamp() WHERE id = ?",
                    live ? "CANCELLED" : "EXPIRED", existing.id());
        }
        UUID id = UUID.randomUUID();
        boolean bank = BANK_TRANSFER.equals(provider);
        Instant expires = Instant.now().plusSeconds(bank ? BANK_TRANSFER_SECONDS : 900);
        // A bank transfer needs no provider link: it is waiting for the money from the start.
        jdbc.queryForObject("""
                INSERT INTO payment_orders (id, user_id, idempotency_key, amount_vnd, ai_limit, period_days, expires_at,
                    provider, status, recipient_account_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING order_code
                """, Long.class, id, userId, key, properties.getProPriceVnd(), usage.getProAiLimit(),
                usage.getPeriodDays(), Timestamp.from(expires), provider, bank ? "PENDING" : "CREATING",
                bank ? properties.getBankAccountNo() : null);
        return require(id, userId);
    }

    @Transactional
    public boolean claimCreate(UUID id) {
        return jdbc.update("""
                UPDATE payment_orders SET provider_attempt_at = clock_timestamp() WHERE id = ? AND status = 'CREATING'
                AND (provider_attempt_at IS NULL OR provider_attempt_at < clock_timestamp() - INTERVAL '30 seconds')
                """, id) == 1;
    }

    @Transactional
    public void recordLink(UUID id, PayOsGateway.Link link) {
        var rows = jdbc.query("SELECT * FROM payment_orders WHERE id = ? FOR UPDATE", (rs, row) -> order(rs), id);
        if (rows.isEmpty()) return;
        Order order = rows.getFirst();
        if (order.orderCode() != link.orderCode() || order.amountVnd() != link.amountVnd()
                || (order.linkId() != null && !order.linkId().equals(link.linkId()))
                || (order.recipientAccountNumber() != null
                    && !order.recipientAccountNumber().equals(link.recipientAccountNumber()))) {
            review(order.id(), "CREATE_LINK_MISMATCH"); return;
        }
        jdbc.update("""
                UPDATE payment_orders SET provider_link_id = ?, checkout_url = ?, recipient_account_number = ?,
                    status = CASE WHEN status = 'CREATING' THEN 'PENDING' ELSE status END, updated_at = clock_timestamp()
                WHERE id = ?
                """, link.linkId(), link.checkoutUrl(), link.recipientAccountNumber(), id);
    }

    @Transactional(readOnly = true)
    public Order require(UUID orderId, UUID userId) {
        return jdbc.query("SELECT * FROM payment_orders WHERE id = ? AND user_id = ?", (rs, row) -> order(rs), orderId, userId)
                .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn thanh toán"));
    }

    @Transactional(readOnly = true)
    public List<Order> history(UUID userId) {
        return jdbc.query("SELECT * FROM payment_orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
                (rs, row) -> order(rs), userId);
    }

    @Transactional(readOnly = true)
    public List<Order> reconcileCandidates() {
        return jdbc.query("""
                SELECT * FROM payment_orders WHERE
                    ((provider = 'PAYOS' AND status IN ('CREATING','PENDING','EXPIRED','CANCELLED','REVIEW')
                        AND created_at > clock_timestamp() - INTERVAL '14 days')
                    OR (status = 'PAID' AND grant_status IN ('PENDING','FAILED')))
                    AND (last_reconciled_at IS NULL OR last_reconciled_at < clock_timestamp() - INTERVAL '5 minutes')
                ORDER BY COALESCE(last_reconciled_at, created_at) ASC LIMIT 30
                """, (rs, row) -> order(rs));
    }

    @Transactional
    public void noteReconcileAttempt(UUID id) {
        jdbc.update("UPDATE payment_orders SET last_reconciled_at = clock_timestamp() WHERE id = ?", id);
    }

    @Transactional
    public Receipt accept(PayOsGateway.VerifiedPayment payment) {
        String fingerprint = digest("WEBHOOK:" + payment.signature());
        var rows = jdbc.query("SELECT * FROM payment_orders WHERE order_code = ? FOR UPDATE",
                (rs, row) -> order(rs), payment.orderCode());
        Order order = rows.isEmpty() ? null : rows.getFirst();
        if (!event(order == null ? null : order.id(), "WEBHOOK", fingerprint, payment.reference(), payment.amountVnd(),
                order == null ? "UNKNOWN_ORDER" : "RECEIVED")) {
            return new Receipt(order == null ? null : order.id(), "DUPLICATE");
        }
        if (order == null) return new Receipt(null, "UNKNOWN_ORDER");
        if (payment.amountVnd() != order.amountVnd() || !"VND".equals(payment.currency())
                || payment.linkId() == null || !payment.linkId().matches("[A-Za-z0-9_-]{10,100}")
                || (order.linkId() != null && !order.linkId().equals(payment.linkId()))
                || (order.recipientAccountNumber() != null && !payment.recipientAccountNumber().isBlank()
                    && !order.recipientAccountNumber().equals(payment.recipientAccountNumber()))) {
            review(order.id(), "WEBHOOK_ORDER_MISMATCH");
            jdbc.update("UPDATE payment_events SET decision = 'REVIEW' WHERE signature_sha256 = ?", fingerprint);
            return new Receipt(order.id(), "REVIEW");
        }
        if (!payment.success()) {
            if (!"PAID".equals(order.status()) && "PENDING".equals(order.status()))
                jdbc.update("UPDATE payment_orders SET status = 'CANCELLED', updated_at = clock_timestamp() WHERE id = ?", order.id());
            return new Receipt(order.id(), "IGNORED");
        }
        Receipt outcome = acceptPaid(order, payment.amountVnd(), payment.currency(), payment.linkId(),
                payment.recipientAccountNumber(), payment.reference());
        jdbc.update("UPDATE payment_events SET decision = ? WHERE signature_sha256 = ?", outcome.decision(), fingerprint);
        return outcome;
    }

    @Transactional
    public Receipt applyProviderStatus(PayOsGateway.Status status) {
        var rows = jdbc.query("SELECT * FROM payment_orders WHERE order_code = ? FOR UPDATE",
                (rs, row) -> order(rs), status.orderCode());
        if (rows.isEmpty()) return new Receipt(null, "UNKNOWN_ORDER");
        Order order = rows.getFirst();
        jdbc.update("UPDATE payment_orders SET last_reconciled_at = clock_timestamp() WHERE id = ?", order.id());
        if (status.amountVnd() != order.amountVnd() || (order.linkId() != null && !order.linkId().equals(status.linkId()))
                || (order.recipientAccountNumber() != null && !status.recipientAccountNumber().isBlank()
                    && !order.recipientAccountNumber().equals(status.recipientAccountNumber()))) {
            review(order.id(), "PROVIDER_STATUS_MISMATCH"); return new Receipt(order.id(), "REVIEW");
        }
        if ("PAID".equals(status.status())) {
            if (status.amountPaidVnd() != order.amountVnd() || status.transactionReference().isBlank()) {
                review(order.id(), "PAID_WITHOUT_EXACT_TRANSACTION"); return new Receipt(order.id(), "REVIEW");
            }
            String fingerprint = digest("QUERY:" + status.orderCode() + ":" + status.transactionReference());
            event(order.id(), "QUERY", fingerprint, status.transactionReference(), status.amountPaidVnd(), "RECEIVED");
            return acceptPaid(order, status.amountPaidVnd(), "VND", status.linkId(),
                    status.recipientAccountNumber(), status.transactionReference());
        }
        String next = PaymentRules.nextUnpaidStatus(order.status(), status.status(), order.expiresAt(), Instant.now());
        if (!next.equals(order.status())) {
            if ("PENDING".equals(next)) jdbc.update("""
                    UPDATE payment_orders SET status = 'PENDING', provider_link_id = ?, checkout_url = ?, updated_at = clock_timestamp()
                    WHERE id = ?
                    """, status.linkId(), "https://pay.payos.vn/web/" + status.linkId(), order.id());
            else jdbc.update("UPDATE payment_orders SET status = ?, updated_at = clock_timestamp() WHERE id = ?", next, order.id());
        }
        return new Receipt(order.id(), "UNCHANGED");
    }

    @Transactional
    public Order cancelBankTransfer(UUID orderId, UUID userId) {
        jdbc.update("""
                UPDATE payment_orders SET status = 'CANCELLED', updated_at = clock_timestamp()
                WHERE id = ? AND user_id = ? AND provider = 'BANK_TRANSFER' AND status = 'PENDING'
                """, orderId, userId);
        return require(orderId, userId);
    }

    /**
     * The admin has seen the money in the bank account. Late money is still honoured,
     * so an expired or cancelled transfer order can be confirmed too.
     */
    @Transactional
    public Order confirmBankTransfer(UUID actorId, UUID orderId, String bankReference) {
        String reference = bankReference == null ? "" : bankReference.trim();
        if (!reference.matches("[A-Za-z0-9._/-]{4,120}")) throw error(HttpStatus.BAD_REQUEST, "Nhập mã giao dịch ngân hàng (4–120 ký tự, không dấu cách)");
        Order order = lockedBankOrder(orderId);
        if ("PAID".equals(order.status())) {
            if (("BANK:" + reference).equals(order.providerReference())) return order;
            throw error(HttpStatus.CONFLICT, "Đơn đã được xác nhận trước đó");
        }
        if (!List.of("PENDING", "EXPIRED", "CANCELLED", "REVIEW").contains(order.status())) throw error(HttpStatus.CONFLICT, "Đơn không ở trạng thái chờ tiền");
        if (!jdbc.queryForList("SELECT id FROM payment_orders WHERE provider_transaction_ref = ?", "BANK:" + reference).isEmpty())
            throw error(HttpStatus.CONFLICT, "Mã giao dịch này đã dùng cho một đơn khác");
        jdbc.update("""
                UPDATE payment_orders SET status = 'PAID', grant_status = 'PENDING', provider_transaction_ref = ?,
                    paid_at = clock_timestamp(), updated_at = clock_timestamp(), review_reason = NULL WHERE id = ?
                """, "BANK:" + reference, orderId);
        adminAudit(actorId, orderId, "CONFIRM_BANK_TRANSFER", "Đã thấy tiền trong tài khoản, mã giao dịch " + reference);
        return adminOrder(orderId);
    }

    @Transactional
    public Order rejectBankTransfer(UUID actorId, UUID orderId, String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.length() < 5 || clean.length() > 1000) throw error(HttpStatus.BAD_REQUEST, "Ghi lý do (5–1000 ký tự)");
        Order order = lockedBankOrder(orderId);
        if (!List.of("PENDING", "EXPIRED", "REVIEW").contains(order.status())) throw error(HttpStatus.CONFLICT, "Đơn không ở trạng thái chờ tiền");
        jdbc.update("UPDATE payment_orders SET status = 'CANCELLED', updated_at = clock_timestamp() WHERE id = ?", orderId);
        adminAudit(actorId, orderId, "REJECT_BANK_TRANSFER", clean);
        return adminOrder(orderId);
    }

    /**
     * A transfer reported by the bank feed (SePay). Pays the order when amount and content match;
     * anything odd goes to the admin's queue as REVIEW instead of turning Pro on.
     */
    @Transactional
    public String acceptBankTransfer(long orderCode, long amount, String bankReference, String eventKey) {
        var rows = jdbc.query("SELECT * FROM payment_orders WHERE order_code = ? FOR UPDATE", (rs, row) -> order(rs), orderCode);
        Order order = rows.isEmpty() ? null : rows.getFirst();
        String fingerprint = digest("BANK:" + eventKey);
        if (!event(order == null ? null : order.id(), "BANK_TRANSFER", fingerprint, bankReference, amount,
                order == null ? "UNKNOWN_ORDER" : "RECEIVED")) return "DUPLICATE";
        String decision = bankDecision(order, amount, bankReference);
        jdbc.update("UPDATE payment_events SET decision = ? WHERE signature_sha256 = ?", decision, fingerprint);
        return decision;
    }

    private String bankDecision(Order order, long amount, String bankReference) {
        if (order == null) return "UNKNOWN_ORDER";
        if (!BANK_TRANSFER.equals(order.provider())) return "NOT_BANK_ORDER";
        if ("PAID".equals(order.status())) return "ALREADY_PAID";
        if (amount != order.amountVnd()) { review(order.id(), "BANK_AMOUNT_MISMATCH"); return "REVIEW"; }
        String reference = "BANK:" + bankReference;
        if (!jdbc.queryForList("SELECT id FROM payment_orders WHERE provider_transaction_ref = ?", reference).isEmpty()) {
            review(order.id(), "DUPLICATE_TRANSACTION_REFERENCE"); return "REVIEW";
        }
        jdbc.update("""
                UPDATE payment_orders SET status = 'PAID', grant_status = 'PENDING', provider_transaction_ref = ?,
                    paid_at = clock_timestamp(), updated_at = clock_timestamp(), review_reason = NULL WHERE id = ?
                """, reference, order.id());
        return "PAID";
    }

    /** When the oldest transfer still waiting for money was created, or null when none is waiting. */
    @Transactional(readOnly = true)
    public Instant oldestOpenBankTransfer() {
        Timestamp oldest = jdbc.queryForObject("""
                SELECT min(created_at) FROM payment_orders WHERE provider = 'BANK_TRANSFER'
                AND status IN ('PENDING', 'EXPIRED') AND created_at > clock_timestamp() - INTERVAL '4 days'
                """, Timestamp.class);
        return instant(oldest);
    }

    @Transactional(readOnly = true)
    public UUID idForCode(long orderCode) {
        return jdbc.query("SELECT id FROM payment_orders WHERE order_code = ?", (rs, row) -> rs.getObject("id", UUID.class), orderCode)
                .stream().findFirst().orElse(null);
    }

    /** Transfers waiting for the admin: odd ones (wrong amount) first, then the newest. */
    @Transactional(readOnly = true)
    public List<BankTransfer> bankTransferQueue() {
        return jdbc.query("""
                SELECT o.id, o.order_code, o.amount_vnd, o.status, o.created_at, u.email, u.full_name
                FROM payment_orders o JOIN users u ON u.id = o.user_id
                WHERE o.provider = 'BANK_TRANSFER' AND o.status IN ('PENDING', 'EXPIRED', 'REVIEW')
                  AND o.created_at > clock_timestamp() - INTERVAL '30 days'
                ORDER BY (o.status = 'REVIEW') DESC, o.created_at DESC LIMIT 100
                """, (rs, row) -> new BankTransfer(rs.getObject("id", UUID.class), rs.getLong("order_code"),
                rs.getLong("amount_vnd"), rs.getString("status"), transferContent(rs.getLong("order_code")),
                rs.getTimestamp("created_at").toInstant(), rs.getString("email"), rs.getString("full_name")));
    }

    /** What the buyer writes in the transfer, so the admin can match the money to the order. */
    public static String transferContent(long orderCode) { return "POSTIVA" + orderCode; }

    private Order lockedBankOrder(UUID orderId) {
        Order order = jdbc.query("SELECT * FROM payment_orders WHERE id = ? FOR UPDATE", (rs, row) -> order(rs), orderId)
                .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn"));
        if (!BANK_TRANSFER.equals(order.provider())) throw error(HttpStatus.CONFLICT, "Đơn này thanh toán qua payOS, không xác nhận tay");
        return order;
    }

    private void adminAudit(UUID actorId, UUID orderId, String action, String reason) {
        jdbc.update("""
                INSERT INTO payment_admin_audit (id, actor_user_id, order_id, action, reason)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), actorId, orderId, action, reason);
    }

    @Transactional
    public void markGrantFailed(UUID id) {
        jdbc.update("UPDATE payment_orders SET grant_status = 'FAILED', updated_at = clock_timestamp() WHERE id = ? AND status = 'PAID' AND grant_status = 'PENDING'", id);
    }

    @Transactional(readOnly = true)
    public List<Order> adminOrders(String status) {
        if (status == null || status.isBlank()) return jdbc.query("SELECT * FROM payment_orders ORDER BY created_at DESC LIMIT 100", (rs, row) -> order(rs));
        if (!List.of("CREATING", "PENDING", "PAID", "CANCELLED", "EXPIRED", "REVIEW").contains(status))
            throw error(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        return jdbc.query("SELECT * FROM payment_orders WHERE status = ? ORDER BY created_at DESC LIMIT 100", (rs, row) -> order(rs), status);
    }

    @Transactional(readOnly = true)
    public Order adminOrder(UUID id) {
        return jdbc.query("SELECT * FROM payment_orders WHERE id = ?", (rs, row) -> order(rs), id)
                .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn"));
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> adminEvents(UUID id) {
        adminOrder(id);
        return jdbc.queryForList("""
                SELECT event_kind, provider_reference, amount_vnd, decision, created_at
                FROM payment_events WHERE order_id = ? ORDER BY created_at DESC LIMIT 100
                """, id);
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> adminAudit(UUID id) {
        adminOrder(id);
        return jdbc.queryForList("""
                SELECT actor_user_id, refund_id, action, reason, created_at
                FROM payment_admin_audit WHERE order_id = ? ORDER BY created_at DESC LIMIT 100
                """, id);
    }

    @Transactional
    public void auditReconcile(UUID actorId, UUID id) {
        jdbc.update("""
                INSERT INTO payment_admin_audit (id, actor_user_id, order_id, action, reason)
                VALUES (?, ?, ?, 'MANUAL_RECONCILE', 'Provider status queried by admin')
                """, UUID.randomUUID(), actorId, id);
    }

    private Receipt acceptPaid(Order order, long amount, String currency, String linkId, String recipient, String reference) {
        String mismatch = PaymentRules.mismatch(order, amount, currency, linkId, recipient, reference);
        if (mismatch != null) {
            review(order.id(), mismatch); return new Receipt(order.id(), "REVIEW");
        }
        var other = jdbc.queryForList("SELECT id FROM payment_orders WHERE provider_transaction_ref = ? AND id <> ?", reference, order.id());
        if (!other.isEmpty() || (order.providerReference() != null && !order.providerReference().equals(reference))) {
            review(order.id(), "DUPLICATE_TRANSACTION_REFERENCE"); return new Receipt(order.id(), "REVIEW");
        }
        if ("PAID".equals(order.status())) return new Receipt(order.id(), "ALREADY_PAID");
        jdbc.update("""
                UPDATE payment_orders SET status = 'PAID', grant_status = 'PENDING', provider_link_id = ?,
                    provider_transaction_ref = ?, recipient_account_number = COALESCE(recipient_account_number, ?),
                    paid_at = clock_timestamp(), updated_at = clock_timestamp(), review_reason = NULL WHERE id = ?
                """, linkId, reference, recipient.isBlank() ? null : recipient, order.id());
        return new Receipt(order.id(), "PAID");
    }

    private boolean event(UUID orderId, String kind, String fingerprint, String reference, long amount, String decision) {
        return jdbc.update("""
                INSERT INTO payment_events (id, order_id, event_kind, signature_sha256, provider_reference, amount_vnd, decision)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (signature_sha256) DO NOTHING
                """, UUID.randomUUID(), orderId, kind, fingerprint, reference, amount < 0 ? null : amount, decision) == 1;
    }

    private void review(UUID id, String reason) {
        jdbc.update("""
                UPDATE payment_orders SET status = CASE WHEN status = 'PAID' THEN status ELSE 'REVIEW' END,
                    review_reason = ?, updated_at = clock_timestamp() WHERE id = ?
                """, reason, id);
    }

    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private Order order(ResultSet rs) throws SQLException {
        Timestamp paid = rs.getTimestamp("paid_at");
        return new Order(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getLong("order_code"),
                rs.getLong("amount_vnd"), rs.getInt("ai_limit"), rs.getInt("period_days"), rs.getString("status"),
                rs.getString("grant_status"), rs.getString("provider_link_id"), rs.getString("checkout_url"),
                rs.getString("recipient_account_number"), rs.getString("provider_transaction_ref"),
                rs.getTimestamp("expires_at").toInstant(), paid == null ? null : paid.toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("granted_period_id", UUID.class), rs.getString("review_reason"),
                rs.getString("provider"));
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }

    public record Order(UUID id, UUID userId, long orderCode, long amountVnd, int aiLimit, int periodDays,
                        String status, String grantStatus, String linkId, String checkoutUrl, String recipientAccountNumber,
                        String providerReference, Instant expiresAt, Instant paidAt, Instant createdAt,
                        UUID periodId, String reviewReason, String provider) { }
    public record BankTransfer(UUID id, long orderCode, long amountVnd, String status, String transferContent,
                               Instant createdAt, String email, String fullName) { }
    public record Receipt(UUID orderId, String decision) { }
}
