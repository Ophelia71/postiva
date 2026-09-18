package com.postiva.scheduler;

import com.postiva.scheduler.service.PublishingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PublishingScheduler {
    private final PublishingService publishingService;

    public PublishingScheduler(PublishingService publishingService) {
        this.publishingService = publishingService;
    }

    @Scheduled(fixedDelayString = "${postiva.scheduler.publish-delay-ms}")
    public void publishDuePosts() {
        publishingService.publishDuePosts();
    }
}
