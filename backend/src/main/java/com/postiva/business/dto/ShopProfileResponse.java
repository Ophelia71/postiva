package com.postiva.business.dto;

import java.time.LocalDateTime;

public record ShopProfileResponse(
        String businessName,
        String industryCode,
        String industryName,
        String mainProducts,
        String targetCustomer,
        String defaultTone,
        String description,
        String highlights,
        String priceRange,
        String phone,
        String address,
        String openingHours,
        String website,
        String policies,
        String signature,
        String brandHashtags,
        /** True once the required fields are filled; profiles from before this feature only have a name. */
        boolean complete,
        LocalDateTime updatedAt
) {
}
