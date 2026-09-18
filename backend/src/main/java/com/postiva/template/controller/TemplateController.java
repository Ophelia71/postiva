package com.postiva.template.controller;

import com.postiva.common.ApiResponse;
import com.postiva.template.entity.IndustryTemplate;
import com.postiva.template.service.TemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<IndustryTemplate>> getTemplates() {
        return ApiResponse.ok(templateService.findAll());
    }

    @GetMapping("/{industryCode}")
    public ApiResponse<IndustryTemplate> getTemplate(@PathVariable String industryCode) {
        return ApiResponse.ok(templateService.findByCode(industryCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy template ngành")));
    }
}
