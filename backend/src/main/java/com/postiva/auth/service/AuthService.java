package com.postiva.auth.service;

import com.postiva.auth.dto.AuthResponse;
import com.postiva.auth.dto.LoginRequest;
import com.postiva.auth.dto.RegisterPendingResponse;
import com.postiva.auth.dto.RegisterRequest;
import com.postiva.auth.dto.UserResponse;
import com.postiva.auth.dto.VerifyRegisterRequest;
import com.postiva.auth.entity.PendingRegistration;
import com.postiva.auth.security.JwtService;
import com.postiva.auth.repository.PendingRegistrationRepository;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private static final Duration REGISTER_OTP_TTL = Duration.ofMinutes(10);
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    private final EmailService emailService;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            EmailService emailService,
            PendingRegistrationRepository pendingRegistrationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.emailService = emailService;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterPendingResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String fullName = request.fullName().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng"
            );
        }

        String code = generateOtp();
        PendingRegistration pendingRegistration = pendingRegistrationRepository
                .findByEmailIgnoreCase(email)
                .orElseGet(PendingRegistration::new);

        pendingRegistration.setFullName(fullName);
        pendingRegistration.setEmail(email);
        pendingRegistration.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        pendingRegistration.setOtpHash(passwordEncoder.encode(code));
        pendingRegistration.setOtpExpiresAt(
                LocalDateTime.now().plus(REGISTER_OTP_TTL)
        );

        pendingRegistrationRepository.save(pendingRegistration);

        try {
            emailService.sendRegistrationOtp(
                    email,
                    fullName,
                    code,
                    REGISTER_OTP_TTL.toMinutes()
            );
        } catch (Exception exception) {
            log.error(
                    "Không thể gửi mã xác thực đăng ký tới {}",
                    email,
                    exception
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể gửi mã xác thực tới email. Vui lòng kiểm tra cấu hình email."
            );
        }

        return new RegisterPendingResponse(
                email,
                REGISTER_OTP_TTL.toSeconds()
        );
    }

    @Transactional
    public AuthResponse verifyRegistration(VerifyRegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            pendingRegistrationRepository.deleteByEmailIgnoreCase(email);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng"
            );
        }

        PendingRegistration pendingRegistration = pendingRegistrationRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy yêu cầu đăng ký hoặc mã đã hết hạn"
                ));

        if (pendingRegistration.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            pendingRegistrationRepository.delete(pendingRegistration);
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Mã xác thực đã hết hạn. Vui lòng đăng ký lại."
            );
        }

        if (!passwordEncoder.matches(request.code(), pendingRegistration.getOtpHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Mã xác thực không chính xác"
            );
        }

        User user = new User();
        user.setFullName(pendingRegistration.getFullName());
        user.setEmail(email);
        user.setPasswordHash(pendingRegistration.getPasswordHash());
        user.setRole(User.Role.USER);

        User savedUser = userRepository.save(user);
        pendingRegistrationRepository.delete(pendingRegistration);

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

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
