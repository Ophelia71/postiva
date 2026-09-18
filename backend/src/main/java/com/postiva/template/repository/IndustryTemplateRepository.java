package com.postiva.template.repository;

import com.postiva.template.entity.IndustryTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IndustryTemplateRepository extends JpaRepository<IndustryTemplate, UUID> {
    Optional<IndustryTemplate> findByIndustryCode(String industryCode);
}

