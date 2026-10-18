package com.postiva.auth.service;

import com.postiva.auth.repository.PendingRegistrationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PendingRegistrationCleanupService {
    private final PendingRegistrationRepository registrations;

    public PendingRegistrationCleanupService(PendingRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    @Transactional
    public void purgeExpired() {
        registrations.deleteByOtpExpiresAtBefore(LocalDateTime.now());
    }
}
