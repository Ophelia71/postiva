package com.postiva.post.entity;

import com.postiva.common.Platform;
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
@Table(name = "product_briefs")
public class ProductBrief {
    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;
    private UUID businessId;

    @Column(nullable = false)
    private String productName;

    private String price;
    private String industryCode;

    @Column(columnDefinition = "TEXT")
    private String targetAudience;

    @Column(columnDefinition = "TEXT")
    private String promotion;

    @Column(name = "promotion", columnDefinition = "TEXT", insertable = false, updatable = false)
    private String highlights;

    private String goal;
    private String tone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Platform> platforms = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String additionalInfo;

    private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() {
        return id;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setBusinessId(UUID businessId) {
        this.businessId = businessId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setProductDescription(String productDescription) {
        // Product description is stored in additionalInfo for compatibility with existing databases.
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public void setIndustryCode(String industryCode) {
        this.industryCode = industryCode;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    public void setHighlights(String highlights) {
        this.promotion = highlights;
        this.highlights = highlights;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public void setPlatforms(List<Platform> platforms) {
        this.platforms = platforms;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }
}
