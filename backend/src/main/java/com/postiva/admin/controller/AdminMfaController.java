package com.postiva.admin.controller;

import com.postiva.admin.service.AdminMfaService;
import com.postiva.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/security/mfa")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMfaController {
    private final AdminMfaService mfa;
    public AdminMfaController(AdminMfaService mfa) { this.mfa = mfa; }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminMfaService.Status>> status() { return ok(mfa.status()); }
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<AdminMfaService.Setup>> setup(@Valid @RequestBody Password request) {
        return ok(mfa.setup(request.currentPassword()));
    }
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<AdminMfaService.RecoveryCodes>> confirm(@Valid @RequestBody Code request) {
        return ok(mfa.confirm(request.code()));
    }
    @PostMapping("/recovery-codes")
    public ResponseEntity<ApiResponse<AdminMfaService.RecoveryCodes>> regenerate(@Valid @RequestBody Proof request) {
        return ok(mfa.regenerate(request.currentPassword(), request.code()));
    }
    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody Proof request) {
        mfa.disable(request.currentPassword(), request.code());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(value));
    }
    public record Password(@NotBlank @Size(max = 72) String currentPassword) { }
    public record Code(@NotBlank @Size(max = 64) String code) { }
    public record Proof(@NotBlank @Size(max = 72) String currentPassword, @NotBlank @Size(max = 64) String code) { }
}
