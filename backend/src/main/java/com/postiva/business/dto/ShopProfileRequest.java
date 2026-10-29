package com.postiva.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The three required fields are what the AI needs to write for this shop; everything else can be added later. */
public record ShopProfileRequest(
        @NotBlank(message = "Nhập tên shop") @Size(max = 255) String businessName,
        /** An industry_templates code, or "other" together with {@link #industryName}. */
        @NotBlank(message = "Chọn ngành hàng") @Size(max = 100) String industryCode,
        @Size(max = 100) String industryName,
        @NotBlank(message = "Nhập sản phẩm hoặc dịch vụ chính") @Size(max = 1000) String mainProducts,
        @Size(max = 1000) String targetCustomer,
        @Size(max = 100) String defaultTone,
        @Size(max = 4000) String description,
        @Size(max = 2000) String highlights,
        @Size(max = 200) String priceRange,
        @Size(max = 50) String phone,
        @Size(max = 500) String address,
        @Size(max = 200) String openingHours,
        @Size(max = 500) String website,
        @Size(max = 2000) String policies,
        @Size(max = 500) String signature,
        @Size(max = 300) String brandHashtags
) {
}
