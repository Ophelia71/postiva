package com.postiva.post.repository;

import com.postiva.post.entity.ProductBrief;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductBriefRepository extends JpaRepository<ProductBrief, UUID> {
}

