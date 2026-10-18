package com.postiva.auth.controller;

import com.postiva.auth.dto.AuthResponse;
import com.postiva.auth.dto.ChangePasswordRequest;
import com.postiva.auth.dto.ForgotPasswordRequest;
import com.postiva.auth.dto.MessageResponse;
import com.postiva.auth.dto.LoginRequest;
import com.postiva.auth.dto.RegisterPendingResponse;
import com.postiva.auth.dto.RegisterRequest;
import com.postiva.auth.dto.ResetPasswordRequest;
import com.postiva.auth.dto.RevokeSessionsRequest;
import com.postiva.auth.dto.UserResponse;
import com.postiva.auth.dto.VerifyRegisterRequest;
import com.postiva.auth.service.AuthService;
import com.postiva.auth.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService passwordRecovery;

    public AuthController(AuthService authService, PasswordRecoveryService passwordRecovery) {
        this.authService = authService;
        this.passwordRecovery = passwordRecovery;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterPendingResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(authService.register(request));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> verifyRegistration(
            @Valid @RequestBody VerifyRegisterRequest request
    ) {
        return ResponseEntity.ok(
                authService.verifyRegistration(request)
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(request));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordRecovery.requestPasswordReset(request.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(
                new MessageResponse("Nếu email thuộc tài khoản đang hoạt động, hướng dẫn khôi phục sẽ được gửi trong ít phút."));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordRecovery.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordRecovery.changePassword(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/sessions/revoke")
    public ResponseEntity<Void> revokeSessions(@Valid @RequestBody RevokeSessionsRequest request) {
        passwordRecovery.revokeSessions(request.currentPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                authService.getCurrentUser(
                        authentication.getName()
                )
        );
    }
}
