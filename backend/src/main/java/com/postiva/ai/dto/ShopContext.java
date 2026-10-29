package com.postiva.ai.dto;

import java.util.ArrayList;
import java.util.List;

/** Facts about the shop that every AI prompt may use. Blank fields are left out of the prompt entirely. */
public record ShopContext(
        String businessName,
        String industry,
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
        /** The shop's signature is appended after generation, so the AI should not write its own contact block. */
        boolean signatureAppended
) {
    /** "- Label: value" lines for the fields that are set, in the order a reader would want them. */
    public List<String> promptLines() {
        List<String> lines = new ArrayList<>();
        add(lines, "Tên shop", businessName);
        add(lines, "Ngành", industry);
        add(lines, "Sản phẩm/dịch vụ chính", mainProducts);
        add(lines, "Khách hàng mục tiêu", targetCustomer);
        add(lines, "Giọng văn mặc định", defaultTone);
        add(lines, "Giới thiệu", description);
        add(lines, "Điểm nổi bật", highlights);
        add(lines, "Khoảng giá", priceRange);
        add(lines, "Số điện thoại/Zalo", phone);
        add(lines, "Địa chỉ", address);
        add(lines, "Giờ mở cửa", openingHours);
        add(lines, "Website/Fanpage", website);
        add(lines, "Chính sách (giao hàng, đổi trả)", policies);
        return lines;
    }

    private static void add(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add("- " + label + ": " + value.strip().replaceAll("\\s*\\R\\s*", " "));
        }
    }
}
