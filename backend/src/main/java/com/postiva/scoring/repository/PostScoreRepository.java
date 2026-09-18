package com.postiva.scoring.repository;

import com.postiva.scoring.entity.PostScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostScoreRepository extends JpaRepository<PostScore, UUID> {
}

