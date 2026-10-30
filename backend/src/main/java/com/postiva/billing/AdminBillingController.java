package com.postiva.billing;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/billing")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('MFA_VERIFIED')")
public class AdminBillingController {
    private final BillingStore store;
    private final BillingService billing;
    private final RefundService refunds;
    private final CurrentUserService current;

    public AdminBillingController(BillingStore store, BillingService billing, RefundService refunds, CurrentUserService current) {
        this.store = store; this.billing = billing; this.refunds = refunds; this.current = current;
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<BillingStore.Order>>> orders(@RequestParam(required = false) String status) {
        return noStore(store.adminOrders(status));
    }

    @GetMapping("/bank-transfers")
    public ResponseEntity<ApiResponse<List<BillingStore.BankTransfer>>> bankTransfers() {
        current.requireCurrentUser();
        return noStore(store.bankTransferQueue());
    }

    @PostMapping("/orders/{id}/confirm-transfer")
    public ResponseEntity<ApiResponse<BillingStore.Order>> confirmTransfer(@PathVariable UUID id, @RequestBody TransferConfirmation input) {
        return noStore(billing.confirmBankTransfer(current.requireCurrentUserId(), id, input.bankReference()));
    }

    @PostMapping("/orders/{id}/reject-transfer")
    public ResponseEntity<ApiResponse<BillingStore.Order>> rejectTransfer(@PathVariable UUID id,
                                                                         @RequestBody BillingController.ReasonRequest input) {
        return noStore(billing.rejectBankTransfer(current.requireCurrentUserId(), id, input.reason()));
    }

    public record TransferConfirmation(String bankReference) { }

    @PostMapping("/orders/{id}/reconcile")
    public ResponseEntity<ApiResponse<BillingStore.Order>> reconcile(@PathVariable UUID id) {
        UUID actor = current.requireCurrentUserId();
        BillingStore.Order result = billing.adminReconcile(id);
        store.auditReconcile(actor, id);
        return noStore(result);
    }

    @GetMapping("/orders/{id}/events")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> events(@PathVariable UUID id) {
        current.requireCurrentUser();
        return noStore(store.adminEvents(id));
    }

    @GetMapping("/orders/{id}/audit")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> audit(@PathVariable UUID id) {
        current.requireCurrentUser();
        return noStore(store.adminAudit(id));
    }

    @PostMapping("/orders/{id}/refund-requests")
    public ResponseEntity<ApiResponse<RefundService.Refund>> request(@PathVariable UUID id,
                                                                      @RequestBody BillingController.ReasonRequest input) {
        return noStore(refunds.adminRequest(current.requireCurrentUserId(), id, input.reason()));
    }

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<RefundService.Refund>>> refunds() {
        current.requireCurrentUser();
        return noStore(refunds.adminRefunds());
    }

    @PostMapping("/refunds/{id}/approve")
    public ResponseEntity<ApiResponse<RefundService.Refund>> approve(@PathVariable UUID id,
                                                                       @RequestBody BillingController.ReasonRequest input) {
        return noStore(refunds.decide(current.requireCurrentUserId(), id, true, input.reason()));
    }

    @PostMapping("/refunds/{id}/reject")
    public ResponseEntity<ApiResponse<RefundService.Refund>> reject(@PathVariable UUID id,
                                                                      @RequestBody BillingController.ReasonRequest input) {
        return noStore(refunds.decide(current.requireCurrentUserId(), id, false, input.reason()));
    }

    @PostMapping("/refunds/{id}/processing")
    public ResponseEntity<ApiResponse<RefundService.Refund>> processing(@PathVariable UUID id,
                                                                          @RequestBody BillingController.ReasonRequest input) {
        return noStore(refunds.startExternalRefund(current.requireCurrentUserId(), id, input.reason()));
    }

    @PostMapping("/refunds/{id}/settle")
    public ResponseEntity<ApiResponse<RefundService.Refund>> settle(@PathVariable UUID id,
                                                                      @RequestBody SettleRequest input) {
        return noStore(refunds.settle(current.requireCurrentUserId(), id, input.providerReference(), input.reason()));
    }

    @PostMapping("/refunds/{id}/fail")
    public ResponseEntity<ApiResponse<RefundService.Refund>> fail(@PathVariable UUID id,
                                                                    @RequestBody BillingController.ReasonRequest input) {
        return noStore(refunds.fail(current.requireCurrentUserId(), id, input.reason()));
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(value));
    }

    public record SettleRequest(String providerReference, String reason) { }
}
