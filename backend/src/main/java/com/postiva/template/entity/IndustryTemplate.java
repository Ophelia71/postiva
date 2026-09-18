package com.postiva.template.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "industry_templates")
public class IndustryTemplate {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String industryCode;

    @Column(nullable = false)
    private String industryName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> postTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> ctaExamples = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> hashtagExamples = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> contentAngles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> toneSuggestions = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getId() {
        return id;
    }

    public String getIndustryCode() {
        return industryCode;
    }

    public void setIndustryCode(String industryCode) {
        this.industryCode = industryCode;
    }

    public String getIndustryName() {
        return industryName;
    }

    public void setIndustryName(String industryName) {
        this.industryName = industryName;
    }

    public List<String> getPostTypes() {
        return postTypes;
    }

    public void setPostTypes(List<String> postTypes) {
        this.postTypes = postTypes;
    }

    public List<String> getCtaExamples() {
        return ctaExamples;
    }

    public void setCtaExamples(List<String> ctaExamples) {
        this.ctaExamples = ctaExamples;
    }

    public List<String> getHashtagExamples() {
        return hashtagExamples;
    }

    public void setHashtagExamples(List<String> hashtagExamples) {
        this.hashtagExamples = hashtagExamples;
    }

    public List<String> getContentAngles() {
        return contentAngles;
    }

    public void setContentAngles(List<String> contentAngles) {
        this.contentAngles = contentAngles;
    }

    public List<String> getToneSuggestions() {
        return toneSuggestions;
    }

    public void setToneSuggestions(List<String> toneSuggestions) {
        this.toneSuggestions = toneSuggestions;
    }
}
