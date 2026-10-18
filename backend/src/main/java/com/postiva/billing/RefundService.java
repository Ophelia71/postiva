package com.postiva.billing;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** payOS payment-link API has no refund endpoint. Settlement is a documented, audited external operation. */
@Service
public class RefundService {
    private final JdbcTemplate jdbc;

    public RefundService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Refund request(UUID userId, UUID orderId, String reason) {
        String clean = reason(reason);
        var orders = jdbc.queryForList("SELECT * FROM payment_orders WHERE id = ? AND user_id = ? FOR UPDATE", orderId, userId);
        if (orders.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn");
        return createRequest(orders.getFirst(), userId, clean);
    }

    @Transactional
    public Refund adminRequest(UUID actorId, UUID orderId, String reason) {
        String clean = reason(reason);
        var orders = jdbc.queryForList("SELECT * FROM payment_orders WHERE id = ? FOR UPDATE", orderId);
        if (orders.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn");
        Refund refund = createRequest(orders.getFirst(), actorId, clean);
        audit(actorId, orderId, refund.id(), "REQUEST_REFUND", clean);
        return refund;
    }

    private Refund createRequest(java.util.Map<String, Object> order, UUID requester, String reason) {
        if (!"PAID".equals(order.get("status"))) throw error(HttpStatus.CONFLICT, "Chỉ đơn đã thanh toán mới có thể yêu cầu hoàn tiền");
        UUID orderId = (UUID) order.get("id");
        var active = jdbc.query("""
                SELECT * FROM payment_refunds WHERE order_id = ? AND status IN
                    ('REQUESTED','APPROVED','PROCESSING','SUCCEEDED') ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> refund(rs), orderId);
        if (!active.isEmpty()) return active.getFirst();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_refunds (id, order_id, amount_vnd, status, reason, requested_by)
                VALUES (?, ?, ?, 'REQUESTED', ?, ?)
                """, id, orderId, order.get("amount_vnd"), reason, requester);
        return require(id);
    }

    @Transactional(readOnly = true)
    public List<Refund> userRefunds(UUID userId, UUID orderId) {
        Integer owned = jdbc.queryForObject("SELECT count(*) FROM payment_orders WHERE id = ? AND user_id = ?", Integer.class, orderId, userId);
        if (owned == null || owned == 0) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn");
        return jdbc.query("SELECT * FROM payment_refunds WHERE order_id = ? ORDER BY created_at DESC", (rs, row) -> refund(rs), orderId);
    }

    @Transactional(readOnly = true)
    public List<Refund> adminRefunds() {
        return jdbc.query("SELECT * FROM payment_refunds ORDER BY created_at DESC LIMIT 100", (rs, row) -> refund(rs));
    }

    @Transactional
    public Refund decide(UUID actorId, UUID refundId, boolean approve, String reason) {
        String clean = reason(reason);
        Refund refund = lockForAdmin(refundId);
        if (!"REQUESTED".equals(refund.status())) throw error(HttpStatus.CONFLICT, "Yêu cầu hoàn đã được xử lý");
        String status = approve ? "APPROVED" : "REJECTED";
        jdbc.update("UPDATE payment_refunds SET status = ?, reviewed_by = ?, reviewed_at = clock_timestamp() WHERE id = ?",
                status, actorId, refundId);
        audit(actorId, refund.orderId(), refundId, approve ? "APPROVE_REFUND" : "REJECT_REFUND", clean);
        return require(refundId);
    }

    @Transactional
    public Refund startExternalRefund(UUID actorId, UUID refundId, String reason) {
        String clean = reason(reason);
        Refund refund = lockForAdmin(refundId);
        if (!"APPROVED".equals(refund.status())) throw error(HttpStatus.CONFLICT, "Yêu cầu hoàn chưa được duyệt");
        jdbc.update("UPDATE payment_refunds SET status = 'PROCESSING' WHERE id = ?", refundId);
        audit(actorId, refund.orderId(), refundId, "START_EXTERNAL_REFUND", clean);
        return require(refundId);
    }

    @Transactional
    public Refund settle(UUID actorId, UUID refundId, String providerReference, String reason) {
        String clean = reason(reason);
        if (providerReference == null || !providerReference.trim().matches("[A-Za-z0-9._:/-]{6,160}"))
            throw error(HttpStatus.BAD_REQUEST, "Mã xác nhận hoàn từ ngân hàng/cổng không hợp lệ");
        Refund refund = lockForAdmin(refundId);
        if ("SUCCEEDED".equals(refund.status()) && providerReference.equals(refund.providerRef())) return refund;
        if (!"PROCESSING".equals(refund.status())) throw error(HttpStatus.CONFLICT, "Yêu cầu hoàn chưa ở trạng thái đang xử lý");
        var duplicates = jdbc.queryForList("SELECT id FROM payment_refunds WHERE provider_ref = ?", providerReference);
        if (!duplicates.isEmpty()) throw error(HttpStatus.CONFLICT, "Mã hoàn tiền đã được dùng");
        Integer others = jdbc.queryForObject("SELECT count(*) FROM payment_refunds WHERE order_id = ? AND status = 'SUCCEEDED'",
                Integer.class, refund.orderId());
        if (others != null && others > 0) throw error(HttpStatus.CONFLICT, "Đơn đã được hoàn tiền");
        jdbc.update("""
                UPDATE payment_refunds SET status = 'SUCCEEDED', provider_ref = ?, settled_by = ?, settled_at = clock_timestamp()
                WHERE id = ?
                """, providerReference, actorId, refundId);
        jdbc.update("""
                UPDATE subscription_periods SET voided_at = clock_timestamp(), void_reason = 'FULL_REFUND'
                WHERE id = (SELECT granted_period_id FROM payment_orders WHERE id = ?) AND voided_at IS NULL
                """, refund.orderId());
        jdbc.update("UPDATE payment_orders SET grant_status = 'NOT_APPLICABLE', updated_at = clock_timestamp() WHERE id = ?", refund.orderId());
        audit(actorId, refund.orderId(), refundId, "CONFIRM_EXTERNAL_REFUND", clean + " | " + providerReference);
        return require(refundId);
    }

    @Transactional
    public Refund fail(UUID actorId, UUID refundId, String reason) {
        String clean = reason(reason);
        Refund refund = lockForAdmin(refundId);
        if (!"PROCESSING".equals(refund.status())) throw error(HttpStatus.CONFLICT, "Yêu cầu hoàn chưa ở trạng thái đang xử lý");
        jdbc.update("UPDATE payment_refunds SET status = 'FAILED' WHERE id = ?", refundId);
        audit(actorId, refund.orderId(), refundId, "EXTERNAL_REFUND_FAILED", clean);
        return require(refundId);
    }

    private Refund lockForAdmin(UUID refundId) {
        var ids = jdbc.queryForList("SELECT order_id FROM payment_refunds WHERE id = ?", refundId);
        if (ids.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hoàn");
        UUID orderId = (UUID) ids.getFirst().get("order_id");
        var owner = jdbc.queryForList("SELECT user_id FROM payment_orders WHERE id = ?", orderId);
        jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", owner.getFirst().get("user_id"));
        jdbc.queryForList("SELECT id FROM payment_orders WHERE id = ? FOR UPDATE", orderId);
        return jdbc.query("SELECT * FROM payment_refunds WHERE id = ? FOR UPDATE", (rs, row) -> refund(rs), refundId).getFirst();
    }

    private Refund require(UUID id) {
        return jdbc.query("SELECT * FROM payment_refunds WHERE id = ?", (rs, row) -> refund(rs), id).getFirst();
    }

    private void audit(UUID actor, UUID order, UUID refund, String action, String reason) {
        jdbc.update("""
                INSERT INTO payment_admin_audit (id, actor_user_id, order_id, refund_id, action, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), actor, order, refund, action, reason);
    }

    private String reason(String value) {
        if (value == null || value.trim().length() < 10 || value.trim().length() > 1000)
            throw error(HttpStatus.BAD_REQUEST, "Lý do phải từ 10 đến 1000 ký tự");
        return value.trim();
    }

    private Refund refund(ResultSet rs) throws SQLException {
        Timestamp settled = rs.getTimestamp("settled_at");
        return new Refund(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getLong("amount_vnd"),
                rs.getString("status"), rs.getString("reason"), rs.getString("provider_ref"),
                rs.getObject("requested_by", UUID.class), rs.getObject("reviewed_by", UUID.class),
                rs.getObject("settled_by", UUID.class), rs.getTimestamp("created_at").toInstant(),
                settled == null ? null : settled.toInstant());
    }

    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }

    public record Refund(UUID id, UUID orderId, long amountVnd, String status, String reason, String providerRef,
                         UUID requestedBy, UUID reviewedBy, UUID settledBy, Instant createdAt, Instant settledAt) { }
}
