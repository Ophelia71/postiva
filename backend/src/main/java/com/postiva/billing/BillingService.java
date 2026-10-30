package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.postiva.usage.UsageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private final BillingProperties properties;
    private final UsageProperties usage;
    private final BillingStore store;
    private final PayOsGateway payos;
    private final BillingGrantService grants;

    public BillingService(BillingProperties properties, UsageProperties usage, BillingStore store,
                          PayOsGateway payos, BillingGrantService grants) {
        this.properties = properties; this.usage = usage; this.store = store; this.payos = payos; this.grants = grants;
    }

    public Offer offer() {
        return new Offer(properties.ready(), properties.getProPriceVnd(), usage.getProAiLimit(), usage.getFreeAiLimit(),
                usage.getPeriodDays(), properties.ready() ? properties.getRefundPolicyUrl() : null);
    }

    public BillingStore.Order create(UUID userId, UUID key) {
        if (!properties.ready()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Gói Pro chưa mở bán; vui lòng liên hệ hỗ trợ");
        BillingStore.Order order = store.draft(userId, key);
        if ("PENDING".equals(order.status())) {
            // An earlier unpaid order is reused only while payOS still waits on it; a link the buyer
            // cancelled on the payOS page must not be handed back as the "new" one.
            BillingStore.Order current = refreshed(order, userId);
            if (!List.of("PENDING", "PAID", "REVIEW").contains(current.status())) order = store.draft(userId, key);
            else return current;
        }
        ensureLink(order);
        return store.require(order.id(), userId);
    }

    /** Asks payOS right away instead of waiting for the webhook or the two-minute reconciliation. */
    public BillingStore.Order refresh(UUID userId, UUID orderId) {
        return refreshed(store.require(orderId, userId), userId);
    }

    private BillingStore.Order refreshed(BillingStore.Order order, UUID userId) {
        try {
            if ("CREATING".equals(order.status()) && order.expiresAt().isAfter(java.time.Instant.now())) ensureLink(order);
            else if ("PENDING".equals(order.status())) reconcileOne(order);
            else if ("PAID".equals(order.status()) && !"GRANTED".equals(order.grantStatus())) grants.grant(order.id());
        } catch (Exception exception) {
            log.warn("On-demand payOS check deferred for order {} ({})", order.id(), exception.getClass().getSimpleName());
        }
        return store.require(order.id(), userId);
    }

    private void ensureLink(BillingStore.Order order) {
        if (!"CREATING".equals(order.status()) || !store.claimCreate(order.id())) return;
        if (order.orderCode() > 999_999_999L) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Mã đơn đã vượt giới hạn payOS");
        try {
            PayOsGateway.Link link = payos.create(order.orderCode(), order.amountVnd(), Long.toString(order.orderCode()),
                    order.expiresAt().getEpochSecond());
            store.recordLink(order.id(), link);
        } catch (Exception exception) {
            // Provider may have created the link before the response was lost. Recover by server-side query.
            log.warn("payOS link creation requires recovery for order {} ({})", order.id(), exception.getClass().getSimpleName());
            try { reconcileOne(order); } catch (Exception lookupFailure) {
                log.warn("payOS link recovery pending for order {} ({})", order.id(), lookupFailure.getClass().getSimpleName());
            }
        }
    }

    public BillingStore.Order cancel(UUID userId, UUID orderId) {
        BillingStore.Order order = store.require(orderId, userId);
        if ("PAID".equals(order.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Đơn đã thanh toán; hãy dùng quy trình yêu cầu hoàn tiền");
        if (!"PENDING".equals(order.status())) return order;
        // Cancelling on the payOS page already closed the link; payOS refuses a second cancel.
        PayOsGateway.Status current = payos.lookup(order.orderCode());
        reconcileReceipt(store.applyProviderStatus("PENDING".equals(current.status()) ? payos.cancel(order.orderCode()) : current));
        return store.require(orderId, userId);
    }

    public String webhook(JsonNode body) {
        if (!properties.providerConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cổng thanh toán chưa được cấu hình");
        BillingStore.Receipt receipt = store.accept(payos.verifyPayment(body));
        reconcileReceipt(receipt);
        return receipt.decision();
    }

    public BillingStore.Order order(UUID userId, UUID id) { return store.require(id, userId); }
    public List<BillingStore.Order> history(UUID userId) { return store.history(userId); }

    @Scheduled(fixedDelay = 120_000, initialDelay = 120_000)
    public void reconcile() {
        if (!properties.providerConfigured()) return;
        for (BillingStore.Order order : store.reconcileCandidates()) {
            try {
                store.noteReconcileAttempt(order.id());
                if ("PAID".equals(order.status())) grants.grant(order.id());
                else if ("CREATING".equals(order.status()) && order.expiresAt().isAfter(java.time.Instant.now())) ensureLink(order);
                else reconcileOne(order);
            } catch (Exception exception) {
                log.warn("Billing reconciliation needs review for order {} ({})", order.id(), exception.getClass().getSimpleName());
            }
        }
    }

    public BillingStore.Order adminReconcile(UUID id) {
        BillingStore.Order order = store.adminOrder(id);
        if (!properties.providerConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cổng thanh toán chưa được cấu hình");
        reconcileOne(order);
        return store.adminOrder(id);
    }

    private void reconcileOne(BillingStore.Order order) {
        PayOsGateway.Status status = payos.lookup(order.orderCode());
        reconcileReceipt(store.applyProviderStatus(status));
    }

    private void reconcileReceipt(BillingStore.Receipt receipt) {
        if (receipt.orderId() == null || "REVIEW".equals(receipt.decision())) return;
        try { grants.grant(receipt.orderId()); }
        catch (Exception exception) {
            store.markGrantFailed(receipt.orderId());
            log.error("Paid order {} awaits entitlement retry ({})", receipt.orderId(), exception.getClass().getSimpleName());
        }
    }

    public record Offer(boolean available, long amountVnd, int aiLimit, int freeAiLimit, int periodDays, String refundPolicyUrl) { }
}
