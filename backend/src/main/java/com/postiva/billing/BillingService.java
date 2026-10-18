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
        return new Offer(properties.ready(), properties.getProPriceVnd(), usage.getProAiLimit(), usage.getPeriodDays(),
                properties.ready() ? properties.getRefundPolicyUrl() : null);
    }

    public BillingStore.Order create(UUID userId, UUID key) {
        if (!properties.ready()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Gói Pro chưa mở bán; vui lòng liên hệ hỗ trợ");
        BillingStore.Order order = store.draft(userId, key);
        ensureLink(order);
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
        reconcileReceipt(store.applyProviderStatus(payos.cancel(order.orderCode())));
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

    public record Offer(boolean available, long amountVnd, int aiLimit, int periodDays, String refundPolicyUrl) { }
}
