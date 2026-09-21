package com.postiva.auth.controller;

import com.postiva.auth.dto.AuthResponse;
import com.postiva.auth.dto.LoginRequest;
import com.postiva.auth.dto.RegisterPendingResponse;
import com.postiva.auth.dto.RegisterRequest;
import com.postiva.auth.dto.UserResponse;
import com.postiva.auth.dto.VerifyRegisterRequest;
import com.postiva.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
        return ResponseEntity.ok(
                authService.login(request)
        );
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
