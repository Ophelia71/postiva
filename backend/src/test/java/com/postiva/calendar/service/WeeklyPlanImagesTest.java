package com.postiva.calendar.service;

import com.postiva.calendar.dto.CreateWeeklyPlanRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which images each post of a week carries. */
class WeeklyPlanImagesTest {
    private final List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    private CreateWeeklyPlanRequest brief(List<UUID> images, String mode) {
        return new CreateWeeklyPlanRequest(UUID.randomUUID(), LocalDate.now(), 4, null, null, "Asia/Ho_Chi_Minh",
                "Vong tay", "Mo ta", null, null, "Khach", "Noi bat", "Muc tieu", null, images, mode);
    }

    @Test
    void defaultKeepsEveryImageOnEveryPost() {
        assertEquals(ids, WeeklyPlanGenerator.imagesFor(brief(ids, null), 0));
        assertEquals(ids, WeeklyPlanGenerator.imagesFor(brief(ids, "ALL"), 3));
    }

    @Test
    void eachGivesOneImagePerPostInOrder() {
        CreateWeeklyPlanRequest each = brief(ids, "EACH");

        for (int position = 0; position < 4; position++) {
            assertEquals(List.of(ids.get(position)), WeeklyPlanGenerator.imagesFor(each, position));
        }
    }

    @Test
    void eachStartsOverWhenThereAreMorePostsThanImages() {
        List<UUID> two = ids.subList(0, 2);

        assertEquals(List.of(two.get(0)), WeeklyPlanGenerator.imagesFor(brief(two, "EACH"), 2));
        assertEquals(List.of(two.get(1)), WeeklyPlanGenerator.imagesFor(brief(two, "EACH"), 3));
    }

    @Test
    void oneImageOrNoneIsLeftAlone() {
        List<UUID> one = ids.subList(0, 1);

        assertEquals(one, WeeklyPlanGenerator.imagesFor(brief(one, "EACH"), 2));
        assertEquals(List.of(), WeeklyPlanGenerator.imagesFor(brief(null, "EACH"), 0));
    }
}
