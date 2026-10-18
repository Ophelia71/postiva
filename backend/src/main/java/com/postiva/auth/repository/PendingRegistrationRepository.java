package com.postiva.auth.repository;

import com.postiva.auth.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {

    Optional<PendingRegistration> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where lower(p.email) = lower(:email)")
    Optional<PendingRegistration> findByEmailForUpdate(@Param("email") String email);

    void deleteByEmailIgnoreCase(String email);

    @Transactional
    void deleteByEmailIgnoreCaseAndOtpHash(String email, String otpHash);

    @Transactional
    int deleteByOtpExpiresAtBefore(java.time.LocalDateTime expiration);
}
