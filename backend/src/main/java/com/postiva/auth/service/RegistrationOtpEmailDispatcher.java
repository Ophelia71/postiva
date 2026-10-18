package com.postiva.auth.service;

import com.postiva.auth.repository.PendingRegistrationRepository;
import com.postiva.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RegistrationOtpEmailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(RegistrationOtpEmailDispatcher.class);

    private final EmailService email;
    private final PendingRegistrationRepository registrations;

    public RegistrationOtpEmailDispatcher(EmailService email, PendingRegistrationRepository registrations) {
        this.email = email;
        this.registrations = registrations;
    }

    @Async("securityEmailExecutor")
    public void send(String recipient, String fullName, String code, String otpHash, long expiresInMinutes) {
        try {
            email.sendRegistrationOtp(recipient, fullName, code, expiresInMinutes);
        } catch (Exception exception) {
            try {
                registrations.deleteByEmailIgnoreCaseAndOtpHash(recipient, otpHash);
                log.warn("Registration OTP email delivery failed; pending credentials were invalidated");
            } catch (Exception cleanupFailure) {
                // Do not log email, OTP, password hashes, or mail-provider exception details.
                log.warn("Registration OTP email delivery failed and pending credentials could not be invalidated");
            }
        }
    }
}
