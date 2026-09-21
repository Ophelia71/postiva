package com.postiva.social.publishing;

import com.postiva.common.Platform;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.entity.SocialAccount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ThreadsPublisher implements PlatformPublisher {
    private static final int MAX_TEXT_LENGTH = 500;
    private static final int MAX_CAROUSEL_ITEMS = 20;

    private final ThreadsGraphClient threadsClient;
    private final FileService fileService;

    public ThreadsPublisher(ThreadsGraphClient threadsClient, FileService fileService) {
        this.threadsClient = threadsClient;
        this.fileService = fileService;
    }

    @Override
    public Platform platform() {
        return Platform.THREADS;
    }

    @Override
    public String publish(GeneratedPost post,
                          SocialAccount account,
                          String accessToken,
                          List<UploadedFile> mediaFiles) {
        String text = PlatformPostContent.require(post, Platform.THREADS);
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalStateException("Nội dung Threads vượt quá 500 ký tự");
        }
        if (mediaFiles.size() > MAX_CAROUSEL_ITEMS) {
            throw new IllegalStateException("Threads carousel hỗ trợ tối đa 20 ảnh/video");
        }
        mediaFiles.forEach(this::validateMedia);

        if (mediaFiles.isEmpty()) {
            String containerId = threadsClient.createTextContainer(accessToken, text);
            return threadsClient.publishContainer(accessToken, containerId);
        }
        if (mediaFiles.size() == 1) {
            UploadedFile media = mediaFiles.get(0);
            String containerId = threadsClient.createMediaContainer(
                    accessToken,
                    text,
                    isVideo(media) ? "VIDEO" : "IMAGE",
                    fileService.publicUrl(media),
                    false
            );
            threadsClient.awaitContainerReady(containerId, accessToken);
            return threadsClient.publishContainer(accessToken, containerId);
        }

        List<String> childIds = new ArrayList<>();
        for (UploadedFile media : mediaFiles) {
            String childId = threadsClient.createMediaContainer(
                    accessToken,
                    null,
                    isVideo(media) ? "VIDEO" : "IMAGE",
                    fileService.publicUrl(media),
                    true
            );
            threadsClient.awaitContainerReady(childId, accessToken);
            childIds.add(childId);
        }
        String containerId = threadsClient.createCarouselContainer(accessToken, text, childIds);
        threadsClient.awaitContainerReady(containerId, accessToken);
        return threadsClient.publishContainer(accessToken, containerId);
    }

    private void validateMedia(UploadedFile media) {
        String mimeType = mimeType(media);
        if (isVideo(media) && !List.of("video/mp4", "video/quicktime").contains(mimeType)) {
            throw new IllegalStateException("Threads chỉ hỗ trợ video MP4 hoặc MOV trong luồng publish hiện tại");
        }
        if (!isImage(media) && !isVideo(media)) {
            throw new IllegalStateException("Threads không hỗ trợ định dạng media " + mimeType);
        }
    }

    private boolean isVideo(UploadedFile media) {
        return mimeType(media).startsWith("video/");
    }

    private boolean isImage(UploadedFile media) {
        return mimeType(media).startsWith("image/");
    }

    private String mimeType(UploadedFile media) {
        return media.getMimeType() == null ? "" : media.getMimeType().toLowerCase();
    }
}
