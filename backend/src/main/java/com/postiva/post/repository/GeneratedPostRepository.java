package com.postiva.post.repository;

import com.postiva.post.entity.GeneratedPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface GeneratedPostRepository extends JpaRepository<GeneratedPost, UUID> {
    Optional<GeneratedPost> findByIdAndUserId(UUID id, UUID userId);

    java.util.List<GeneratedPost> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
