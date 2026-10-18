package com.postiva.auth.service;

import com.postiva.auth.dto.AuthResponse;
import com.postiva.admin.service.AdminMfaService;
import com.postiva.auth.dto.LoginRequest;
import com.postiva.auth.dto.RegisterPendingResponse;
import com.postiva.auth.dto.RegisterRequest;
import com.postiva.auth.dto.UserResponse;
import com.postiva.auth.dto.VerifyRegisterRequest;
import com.postiva.auth.entity.PendingRegistration;
import com.postiva.auth.security.JwtService;
import com.postiva.auth.security.AuthRequestGuard;
import com.postiva.usage.AiUsageService;
import com.postiva.auth.repository.PendingRegistrationRepository;
import com.postiva.mail.EmailService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private static final Duration REGISTER_OTP_TTL = Duration.ofMinutes(10);
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    private final EmailService emailService;
    private final RegistrationOtpEmailDispatcher registrationOtpEmailDispatcher;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdminMfaService mfa;
    private final AuthRequestGuard guard;
    private final TransactionTemplate transactions;
    private final AiUsageService usage;
    private final JdbcTemplate jdbc;

    public AuthService(
            EmailService emailService,
            RegistrationOtpEmailDispatcher registrationOtpEmailDispatcher,
            PendingRegistrationRepository pendingRegistrationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AdminMfaService mfa,
            AuthRequestGuard guard,
            PlatformTransactionManager transactionManager,
            AiUsageService usage,
            JdbcTemplate jdbc
    ) {
        this.emailService = emailService;
        this.registrationOtpEmailDispatcher = registrationOtpEmailDispatcher;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.mfa = mfa;
        this.guard = guard;
        this.transactions = new TransactionTemplate(transactionManager);
        this.usage = usage;
        this.jdbc = jdbc;
    }

    public RegisterPendingResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        guard.registration(email);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Mật khẩu không được vượt quá 72 byte UTF-8");
        }
        RegistrationDelivery delivery = transactions.execute(status -> prepareRegistration(request, email));
        if (delivery != null && delivery.code() != null) {
            try {
                // Queue mail only after the OTP hash commits; the response never depends on SMTP.
                registrationOtpEmailDispatcher.send(email, delivery.fullName(), delivery.code(),
                        delivery.otpHash(), REGISTER_OTP_TTL.toMinutes());
            } catch (RuntimeException exception) {
                invalidatePendingRegistration(email, delivery.otpHash());
                log.warn("Registration OTP email could not be queued");
            }
        }
        return new RegisterPendingResponse(email, REGISTER_OTP_TTL.toSeconds());
    }

    private record RegistrationDelivery(String fullName, String code, String otpHash) { }

    private RegistrationDelivery prepareRegistration(RegisterRequest request, String email) {
        lockRegistration(email);
        String fullName = request.fullName().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Match the BCrypt work performed for a new address to reduce timing-based enumeration.
            passwordEncoder.encode(request.password());
            passwordEncoder.encode("000000");
            return new RegistrationDelivery(fullName, null, null);
        }

        String code = generateOtp();
        PendingRegistration pendingRegistration = pendingRegistrationRepository
                .findByEmailForUpdate(email)
                .orElseGet(PendingRegistration::new);

        pendingRegistration.setFullName(fullName);
        pendingRegistration.setEmail(email);
        pendingRegistration.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        String otpHash = passwordEncoder.encode(code);
        pendingRegistration.setOtpHash(otpHash);
        pendingRegistration.resetFailedAttempts();
        pendingRegistration.setOtpExpiresAt(
                LocalDateTime.now().plus(REGISTER_OTP_TTL)
        );

        pendingRegistrationRepository.save(pendingRegistration);

        return new RegistrationDelivery(fullName, code, otpHash);
    }

    public AuthResponse verifyRegistration(VerifyRegisterRequest request) {
        String email = normalizeEmail(request.email());
        guard.verification(email);
        Verification result = transactions.execute(status -> {
            try { return new Verification(verifyInTransaction(request, email), null); }
            catch (ResponseStatusException e) { return new Verification(null, e); }
        });
        if (result.error() != null) throw result.error();
        userRepository.findByEmailIgnoreCase(email).ifPresent(this::sendRegistrationEmailSafely);
        return result.response();
    }

    private record Verification(AuthResponse response, ResponseStatusException error) { }

    private AuthResponse verifyInTransaction(VerifyRegisterRequest request, String email) {
        lockRegistration(email);
        PendingRegistration pendingRegistration = pendingRegistrationRepository
                .findByEmailForUpdate(email)
                .orElseThrow(() -> {
                    burnOtpCheck(request.code());
                    return invalidOtp();
                });

        if (!pendingRegistration.getOtpExpiresAt().isAfter(LocalDateTime.now())) {
            burnOtpCheck(request.code());
            pendingRegistrationRepository.delete(pendingRegistration);
            throw invalidOtp();
        }

        if (pendingRegistration.getFailedAttempts() >= 5) {
            burnOtpCheck(request.code());
            throw invalidOtp();
        }
        if (!passwordEncoder.matches(request.code(), pendingRegistration.getOtpHash())) {
            pendingRegistration.failAttempt();
            throw invalidOtp();
        }

        User user = new User();
        user.setFullName(pendingRegistration.getFullName());
        user.setEmail(email);
        user.setPasswordHash(pendingRegistration.getPasswordHash());
        user.setRole(User.Role.USER);

        User savedUser = userRepository.saveAndFlush(user);
        usage.summary(savedUser.getId());
        pendingRegistrationRepository.delete(pendingRegistration);

        String token = jwtService.generateToken(savedUser);

        return createAuthResponse(token, savedUser);
    }

    private void lockRegistration(String email) {
        // Serializes creation/resend/verification even before a pending-registration row exists.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", email);
    }

    private void invalidatePendingRegistration(String email, String otpHash) {
        try {
            pendingRegistrationRepository.deleteByEmailIgnoreCaseAndOtpHash(email, otpHash);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Registration OTP could not be queued and pending credentials could not be invalidated");
        }
    }

    private void burnOtpCheck(String code) {
        passwordEncoder.encode(code == null ? "000000" : code);
    }

    private ResponseStatusException invalidOtp() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Mã xác thực không hợp lệ hoặc đã hết hạn");
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        guard.login(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean mfaVerified = false;

        if (user != null && user.getRole() == User.Role.ADMIN) {
            AdminMfaService.VerifiedLogin verified = mfa.verifyLogin(user.getId(), request.password(), request.mfaCode());
            user = verified.user();
            mfaVerified = verified.verified();
        } else try {
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

        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác");
        String token = jwtService.generateToken(user, mfaVerified);

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
                null,
                user.getRole() == User.Role.ADMIN && !user.isMfaEnabled()
        );
    }

    private void sendRegistrationEmailSafely(User user) {
        try {
            emailService.sendRegistrationSuccess(
                    user.getEmail(),
                    user.getFullName()
            );

            log.info(
                    "Registration confirmation email sent"
            );
        } catch (Exception exception) {
            log.warn("Registration confirmation email delivery failed");
        }
    }

    private void sendLoginEmailSafely(User user) {
        try {
            emailService.sendLoginNotification(
                    user.getEmail(),
                    user.getFullName()
            );

            log.info(
                    "Login notification email sent"
            );
        } catch (Exception exception) {
            log.warn("Login notification email delivery failed");
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
