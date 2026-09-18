package com.postiva.template.service;

import com.postiva.template.entity.IndustryTemplate;
import com.postiva.template.repository.IndustryTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TemplateService {
    private final IndustryTemplateRepository repository;

    public TemplateService(IndustryTemplateRepository repository) {
        this.repository = repository;
    }

    public List<IndustryTemplate> findAll() {
        return repository.findAll();
    }

    public Optional<IndustryTemplate> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIndustryCode(code);
    }
}
