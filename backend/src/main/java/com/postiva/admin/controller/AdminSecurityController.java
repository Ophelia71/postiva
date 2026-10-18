package com.postiva.admin.controller;

import com.postiva.admin.dto.ChangeAdminPasswordRequest;
import com.postiva.admin.service.AdminPasswordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSecurityController {
    private final AdminPasswordService passwords;

    public AdminSecurityController(AdminPasswordService passwords) {
        this.passwords = passwords;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangeAdminPasswordRequest request) {
        passwords.changePassword(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
