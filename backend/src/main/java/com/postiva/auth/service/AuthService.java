package com.postiva.auth.service;

import com.postiva.auth.dto.AuthResponse;
import com.postiva.auth.dto.LoginRequest;
import com.postiva.auth.dto.RegisterRequest;
import com.postiva.business.entity.BusinessProfile;
import com.postiva.business.repository.BusinessProfileRepository;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(UserRepository userRepository,
                       BusinessProfileRepository businessProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.businessProfileRepository = businessProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(User.Role.USER);
        User savedUser = userRepository.save(user);

        BusinessProfile workspace = new BusinessProfile();
        workspace.setUserId(savedUser.getId());
        workspace.setBusinessName(savedUser.getFullName());
        businessProfileRepository.save(workspace);

        return response(savedUser, workspace);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        }
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse me() {
        return response(currentUserService.requireCurrentUser());
    }

    private AuthResponse response(User user) {
        return response(user, requireWorkspace(user));
    }

    private AuthResponse response(User user, BusinessProfile workspace) {
        return new AuthResponse(
                jwtService.createToken(user.getEmail()),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                workspace.getId(),
                workspace.getBusinessName()
        );
    }

    private BusinessProfile requireWorkspace(User user) {
        return businessProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Tài khoản chưa có workspace. Vui lòng tạo lại workspace cho tài khoản này."
                ));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
