package com.postiva.calendar.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateWeeklyPlanRequest(
        @NotNull UUID socialAccountId,
        /** First day of the planned week, in the plan's timezone. */
        @NotNull LocalDate startDate,
        @NotNull @Min(1) @Max(7) Integer postsPerWeek,
        /** Optional ISO weekdays (1 = Monday) the shop prefers; otherwise days come from past-post data. */
        @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> daysOfWeek,
        /** Optional fixed posting time "HH:mm"; otherwise the best hour from past posts, or 19:00. */
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ đăng phải có dạng HH:mm") String postTime,
        @Size(max = 64) String timezone,
        @NotBlank @Size(max = 200) String productName,
        @NotBlank @Size(max = 4000) String productDescription,
        @Size(max = 100) String price,
        @Size(max = 100) String industryCode,
        @NotBlank @Size(max = 1000) String targetAudience,
        @NotBlank @Size(max = 2000) String highlights,
        @NotBlank @Size(max = 255) String goal,
        @Size(max = 500) String tone,
        /** Images or videos attached to every post of the week; Instagram needs at least one. */
        @Size(max = 20) List<@NotNull UUID> imageIds,
        /** ALL (default) puts every image on every post; EACH gives each post one image, taking them in turn. */
        @Pattern(regexp = "^(ALL|EACH)$", message = "Cách dùng ảnh phải là ALL hoặc EACH") String imageMode
) {
}
