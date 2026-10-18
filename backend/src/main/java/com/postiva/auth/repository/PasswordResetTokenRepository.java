package com.postiva.auth.repository;

import com.postiva.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Query("select token.userId from PasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken token set token.usedAt = :usedAt " +
            "where token.userId = :userId and token.usedAt is null")
    int invalidateActiveForUser(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);

    @Modifying
    @Transactional
    @Query("update PasswordResetToken token set token.usedAt = :usedAt " +
            "where token.tokenHash = :tokenHash and token.usedAt is null")
    int invalidateTokenHash(@Param("tokenHash") String tokenHash, @Param("usedAt") Instant usedAt);

    @Modifying
    @Transactional
    @Query("delete from PasswordResetToken token where token.expiresAt < :cutoff " +
            "or token.usedAt < :cutoff")
    int purgeOldTokens(@Param("cutoff") Instant cutoff);
}
