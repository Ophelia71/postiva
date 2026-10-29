package com.postiva.ai.service;

import com.postiva.ai.dto.ShopContext;
import com.postiva.common.Platform;
import com.postiva.post.dto.GeneratePostRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPromptTest {
    private final PromptBuilder builder = new PromptBuilder();
    private final GeneratePostRequest request = new GeneratePostRequest("Cà phê muối", "Pha phin", null, null,
            "Dân văn phòng", "Giao 30 phút", "Tăng đơn", "Thân thiện", List.of(Platform.FACEBOOK), null, List.of());

    @Test
    void shopProfileIsGivenAsDataWithContactRule() {
        ShopContext shop = new ShopContext("Tiệm Muối", "Café / Đồ uống", "Cà phê muối, bánh quy", null, null, null,
                null, null, "0901 234 567", "12 Lê Lợi", null, null, null, true);

        String prompt = builder.buildGeneratePostPrompt(request, null, shop);

        assertTrue(prompt.contains("- Tên shop: Tiệm Muối"));
        assertTrue(prompt.contains("- Số điện thoại/Zalo: 0901 234 567"));
        assertTrue(prompt.contains("chỉ được dùng đúng như trong phần hồ sơ shop"));
        assertTrue(prompt.contains("hệ thống sẽ tự thêm chữ ký của shop"));
        // Blank fields are left out instead of appearing as empty labels.
        assertFalse(prompt.contains("Giờ mở cửa"));
        // The profile sits inside the data block, before its closing tag.
        assertTrue(prompt.indexOf("Hồ sơ shop") < prompt.indexOf("</product_input>"));
    }

    @Test
    void withoutProfilePromptKeepsTheNoInventedContactRuleOnly() {
        String prompt = builder.buildGeneratePostPrompt(request, null);

        assertFalse(prompt.contains("Hồ sơ shop"));
        assertFalse(prompt.contains("chữ ký của shop"));
        assertTrue(prompt.contains("Không tự bịa địa chỉ, số điện thoại"));
    }
}
