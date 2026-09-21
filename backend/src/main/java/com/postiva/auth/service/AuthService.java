package com.postiva.auth.service;

import com.postiva.auth.dto.AuthResponse;
import com.postiva.auth.dto.LoginRequest;
import com.postiva.auth.dto.RegisterRequest;
import com.postiva.auth.dto.UserResponse;
import com.postiva.auth.security.JwtService;
import com.postiva.mail.EmailService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            EmailService emailService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String fullName = request.fullName().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng"
            );
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setRole(User.Role.USER);

        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);

        sendRegistrationEmailSafely(savedUser);

        return createAuthResponse(token, savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Email hoặc mật khẩu không chính xác"
            );
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Email hoặc mật khẩu không chính xác"
                ));

        String token = jwtService.generateToken(user);

        sendLoginEmailSafely(user);

        return createAuthResponse(token, user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(
                        normalizeEmail(email)
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng"
                ));

        return UserResponse.from(user);
    }

    private AuthResponse createAuthResponse(
            String token,
            User user
    ) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                null,
                null
        );
    }

    private void sendRegistrationEmailSafely(User user) {
        try {
            emailService.sendRegistrationSuccess(
                    user.getEmail(),
                    user.getFullName()
            );

            log.info(
                    "Đã gửi email đăng ký tới {}",
                    user.getEmail()
            );
        } catch (Exception exception) {
            log.error(
                    "Không thể gửi email đăng ký tới {}",
                    user.getEmail(),
                    exception
            );
        }
    }

    private void sendLoginEmailSafely(User user) {
        try {
            emailService.sendLoginNotification(
                    user.getEmail(),
                    user.getFullName()
            );

            log.info(
                    "Đã gửi email thông báo đăng nhập tới {}",
                    user.getEmail()
            );
        } catch (Exception exception) {
            log.error(
                    "Không thể gửi email thông báo đăng nhập tới {}",
                    user.getEmail(),
                    exception
            );
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email không được để trống"
            );
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }
}
