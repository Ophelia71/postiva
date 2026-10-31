package com.postiva.ai.dto;

import com.postiva.common.Platform;

import java.util.List;

/** Everything the weekly planner prompt is built from; all strings come from the shop or from Meta. */
public record WeeklyPlanPromptInput(
        String productName,
        String productDescription,
        String price,
        String industryCode,
        String targetAudience,
        String highlights,
        String goal,
        String tone,
        Platform platform,
        /** "yyyy-MM-dd (Thứ X) HH:mm" for each day to plan, in order. */
        List<String> slots,
        List<String> findings,
        List<String> topPostExcerpts,
        List<String> templatePostTypes,
        List<String> templateContentAngles,
        /** The shop's saved profile, or null when it has none yet. */
        ShopContext shop
) {
}
