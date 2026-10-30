package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billing;
    private final RefundService refunds;
    private final CurrentUserService current;

    public BillingController(BillingService billing, RefundService refunds, CurrentUserService current) {
        this.billing = billing; this.refunds = refunds; this.current = current;
    }

    @GetMapping("/offer")
    public ResponseEntity<ApiResponse<BillingService.Offer>> offer() {
        return noStore(billing.offer());
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<BillingStore.Order>> create(@RequestHeader("Idempotency-Key") UUID key) {
        return noStore(billing.create(current.requireCurrentUserId(), key));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<BillingStore.Order>>> history() {
        return noStore(billing.history(current.requireCurrentUserId()));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<BillingStore.Order>> order(@PathVariable UUID id) {
        return noStore(billing.order(current.requireCurrentUserId(), id));
    }

    @PostMapping("/orders/{id}/refresh")
    public ResponseEntity<ApiResponse<BillingStore.Order>> refresh(@PathVariable UUID id) {
        return noStore(billing.refresh(current.requireCurrentUserId(), id));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<ApiResponse<BillingStore.Order>> cancel(@PathVariable UUID id) {
        return noStore(billing.cancel(current.requireCurrentUserId(), id));
    }

    @PostMapping("/orders/{id}/refund-requests")
    public ResponseEntity<ApiResponse<RefundService.Refund>> requestRefund(@PathVariable UUID id,
                                                                            @RequestBody ReasonRequest request) {
        return noStore(refunds.request(current.requireCurrentUserId(), id, request.reason()));
    }

    @GetMapping("/orders/{id}/refund-requests")
    public ResponseEntity<ApiResponse<List<RefundService.Refund>>> refunds(@PathVariable UUID id) {
        return noStore(refunds.userRefunds(current.requireCurrentUserId(), id));
    }

    /** Only this signed server-to-server notification or a signed provider query can record payment. */
    @PostMapping("/payos/webhook")
    public ResponseEntity<ApiResponse<String>> webhook(@RequestBody JsonNode body) {
        return noStore(billing.webhook(body));
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(value));
    }

    public record ReasonRequest(String reason) { }
}
