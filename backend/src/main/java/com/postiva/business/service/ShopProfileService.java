package com.postiva.business.service;

import com.postiva.ai.dto.ShopContext;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.business.dto.ShopProfileRequest;
import com.postiva.business.dto.ShopProfileResponse;
import com.postiva.business.entity.BusinessProfile;
import com.postiva.business.repository.BusinessProfileRepository;
import com.postiva.template.entity.IndustryTemplate;
import com.postiva.template.service.TemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class ShopProfileService {
    /** Industry code used when the shop's trade is not one of the seeded templates. */
    public static final String OTHER_INDUSTRY = "other";

    private final BusinessProfileRepository repository;
    private final CurrentUserService currentUser;
    private final TemplateService templates;

    public ShopProfileService(BusinessProfileRepository repository, CurrentUserService currentUser,
                              TemplateService templates) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.templates = templates;
    }

    /** Empty when the shop has never saved a profile. */
    @Transactional(readOnly = true)
    public Optional<ShopProfileResponse> current() {
        return repository.findByUserId(currentUser.requireCurrentUserId()).map(this::response);
    }

    @Transactional
    public ShopProfileResponse save(ShopProfileRequest request) {
        UUID userId = currentUser.requireCurrentUserId();
        String industryCode = request.industryCode().trim();
        String industryName = trimToNull(request.industryName());
        if (OTHER_INDUSTRY.equals(industryCode)) {
            if (industryName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập tên ngành hàng của shop");
            }
        } else if (templates.findByCode(industryCode).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngành hàng không hợp lệ");
        } else {
            industryName = null;
        }

        BusinessProfile profile = repository.findByUserId(userId).orElseGet(() -> {
            BusinessProfile created = new BusinessProfile();
            created.setUserId(userId);
            return created;
        });
        profile.setBusinessName(request.businessName().trim());
        profile.setIndustryCode(industryCode);
        profile.setIndustryName(industryName);
        profile.setMainProducts(request.mainProducts().trim());
        profile.setTargetCustomer(trimToNull(request.targetCustomer()));
        profile.setDefaultTone(trimToNull(request.defaultTone()));
        profile.setDescription(trimToNull(request.description()));
        profile.setHighlights(trimToNull(request.highlights()));
        profile.setPriceRange(trimToNull(request.priceRange()));
        profile.setPhone(trimToNull(request.phone()));
        profile.setAddress(trimToNull(request.address()));
        profile.setOpeningHours(trimToNull(request.openingHours()));
        profile.setWebsite(trimToNull(request.website()));
        profile.setPolicies(trimToNull(request.policies()));
        profile.setSignature(trimToNull(request.signature()));
        profile.setBrandHashtags(trimToNull(request.brandHashtags()));
        return response(repository.save(profile));
    }

    /** The profile as AI input, for work that knows the user but runs outside a request (weekly plans). */
    @Transactional(readOnly = true)
    public Optional<BusinessProfile> forUser(UUID userId) {
        return repository.findByUserId(userId);
    }

    public ShopContext context(BusinessProfile profile) {
        return new ShopContext(profile.getBusinessName(), industryLabel(profile), profile.getMainProducts(),
                profile.getTargetCustomer(), profile.getDefaultTone(), profile.getDescription(), profile.getHighlights(),
                profile.getPriceRange(), profile.getPhone(), profile.getAddress(), profile.getOpeningHours(),
                profile.getWebsite(), profile.getPolicies(), hasText(profile.getSignature()));
    }

    /** The template code to use for AI drafts, or null for "other" and unknown industries. */
    public static String templateCode(BusinessProfile profile) {
        String code = profile.getIndustryCode();
        return code == null || code.isBlank() || OTHER_INDUSTRY.equals(code) ? null : code;
    }

    public static boolean isComplete(BusinessProfile profile) {
        return hasText(profile.getBusinessName()) && hasText(profile.getIndustryCode()) && hasText(profile.getMainProducts())
                && (!OTHER_INDUSTRY.equals(profile.getIndustryCode()) || hasText(profile.getIndustryName()));
    }

    private String industryLabel(BusinessProfile profile) {
        if (OTHER_INDUSTRY.equals(profile.getIndustryCode())) return profile.getIndustryName();
        if (!hasText(profile.getIndustryCode())) return null;
        return templates.findByCode(profile.getIndustryCode()).map(IndustryTemplate::getIndustryName)
                .orElse(profile.getIndustryCode());
    }

    private ShopProfileResponse response(BusinessProfile profile) {
        return new ShopProfileResponse(profile.getBusinessName(), profile.getIndustryCode(), profile.getIndustryName(),
                profile.getMainProducts(), profile.getTargetCustomer(), profile.getDefaultTone(), profile.getDescription(),
                profile.getHighlights(), profile.getPriceRange(), profile.getPhone(), profile.getAddress(),
                profile.getOpeningHours(), profile.getWebsite(), profile.getPolicies(), profile.getSignature(),
                profile.getBrandHashtags(), isComplete(profile), profile.getUpdatedAt());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
