package com.postiva.calendar.service;

import com.postiva.calendar.dto.CalendarItemResponse;
import com.postiva.calendar.dto.CalendarResponse;
import com.postiva.calendar.dto.GenerateCalendarRequest;
import com.postiva.calendar.entity.CalendarItem;
import com.postiva.calendar.entity.ContentCalendar;
import com.postiva.calendar.repository.CalendarItemRepository;
import com.postiva.calendar.repository.ContentCalendarRepository;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.template.entity.IndustryTemplate;
import com.postiva.template.service.TemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CalendarService {
    private final ContentCalendarRepository calendarRepository;
    private final CalendarItemRepository itemRepository;
    private final TemplateService templateService;
    private final CurrentUserService currentUserService;

    public CalendarService(ContentCalendarRepository calendarRepository,
                           CalendarItemRepository itemRepository,
                           TemplateService templateService,
                           CurrentUserService currentUserService) {
        this.calendarRepository = calendarRepository;
        this.itemRepository = itemRepository;
        this.templateService = templateService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public CalendarResponse generate(GenerateCalendarRequest request) {
        java.util.UUID userId = currentUserService.requireCurrentUserId();
        IndustryTemplate template = templateService.findByCode(request.industryCode())
                .orElseThrow(() -> new IllegalArgumentException("Ngành nghề chưa có template"));

        ContentCalendar calendar = new ContentCalendar();
        calendar.setUserId(userId);
        calendar.setIndustryCode(request.industryCode());
        calendar.setGoal(request.goal());
        calendar.setStartDate(request.startDate());
        calendar.setEndDate(request.startDate().plusDays(6));
        calendar = calendarRepository.save(calendar);

        List<String> postTypes = template.getPostTypes().isEmpty()
                ? List.of("Giới thiệu sản phẩm", "Ưu đãi", "Feedback", "Hậu trường")
                : template.getPostTypes();
        List<CalendarItem> savedItems = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = request.startDate().plusDays(i);
            CalendarItem item = new CalendarItem();
            item.setCalendarId(calendar.getId());
            item.setPlannedDate(date);
            item.setPlatform(i % 2 == 0 ? Platform.FACEBOOK : Platform.INSTAGRAM);
            item.setPostType(postTypes.get(i % postTypes.size()));
            item.setTopic(item.getPostType() + " cho mục tiêu: " + request.goal());
            item.setStatus(PostStatus.PLANNED);
            savedItems.add(itemRepository.save(item));
        }

        return toResponse(calendar, savedItems);
    }

    public CalendarResponse getCalendar(java.util.UUID calendarId) {
        java.util.UUID userId = currentUserService.requireCurrentUserId();
        ContentCalendar calendar = calendarRepository.findByIdAndUserId(calendarId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch nội dung"));
        return toResponse(calendar, itemRepository.findByCalendarIdOrderByPlannedDateAsc(calendarId));
    }

    private CalendarResponse toResponse(ContentCalendar calendar, List<CalendarItem> items) {
        return new CalendarResponse(
                calendar.getId(),
                calendar.getIndustryCode(),
                calendar.getGoal(),
                calendar.getStartDate(),
                calendar.getEndDate(),
                items.stream()
                        .map(item -> new CalendarItemResponse(
                                item.getPlannedDate(),
                                item.getPlatform().name(),
                                item.getPostType(),
                                item.getTopic(),
                                item.getStatus().name()))
                        .toList()
        );
    }
}
