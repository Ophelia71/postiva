package com.postiva.social.repository;

import com.postiva.common.Platform;
import com.postiva.social.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
    List<SocialAccount> findByUserIdAndDisconnectedAtIsNullOrderByConnectedAtDesc(UUID userId);
    Optional<SocialAccount> findByIdAndUserIdAndDisconnectedAtIsNull(UUID id, UUID userId);
    Optional<SocialAccount> findByIdAndDisconnectedAtIsNull(UUID id);
    Optional<SocialAccount> findByUserIdAndProviderAndPageId(UUID userId, Platform provider, String pageId);
}
