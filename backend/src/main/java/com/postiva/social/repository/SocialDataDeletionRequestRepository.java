package com.postiva.social.repository;

import com.postiva.common.Platform;
import com.postiva.social.entity.SocialDataDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocialDataDeletionRequestRepository extends JpaRepository<SocialDataDeletionRequest, UUID> {
    Optional<SocialDataDeletionRequest> findByConfirmationCodeAndProvider(
            String confirmationCode,
            Platform provider
    );
}
