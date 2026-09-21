package com.postiva.auth.repository;

import com.postiva.auth.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {

    Optional<PendingRegistration> findByEmailIgnoreCase(String email);

    void deleteByEmailIgnoreCase(String email);
}
