package com.postiva.social.publishing;

import com.postiva.common.Platform;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.entity.SocialAccount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class InstagramPublisher implements PlatformPublisher {
    private static final int MAX_CAPTION_LENGTH = 2_200;
    private static final int MAX_CAROUSEL_ITEMS = 10;

    private final InstagramGraphClient instagramClient;
    private final FileService fileService;

    public InstagramPublisher(InstagramGraphClient instagramClient, FileService fileService) {
        this.instagramClient = instagramClient;
        this.fileService = fileService;
    }

    @Override
    public Platform platform() {
        return Platform.INSTAGRAM;
    }

    @Override
    public String publish(GeneratedPost post,
                          SocialAccount account,
                          String accessToken,
                          List<UploadedFile> mediaFiles) {
        String caption = PlatformPostContent.require(post, Platform.INSTAGRAM);
        if (caption.length() > MAX_CAPTION_LENGTH) {
            throw new IllegalStateException("Caption Instagram vượt quá 2.200 ký tự");
        }
        if (mediaFiles.isEmpty()) {
            throw new IllegalStateException("Instagram cần ít nhất một ảnh hoặc video để đăng bài");
        }
        if (mediaFiles.size() > MAX_CAROUSEL_ITEMS) {
            throw new IllegalStateException("Instagram carousel hỗ trợ tối đa 10 ảnh/video");
        }
        mediaFiles.forEach(this::validateMedia);

        InstagramGraphClient.ApiMode apiMode = apiMode(account);
        String accountId = account.getPageId();
        if (mediaFiles.size() == 1) {
            UploadedFile media = mediaFiles.get(0);
            String containerId = isVideo(media)
                    ? instagramClient.createVideoContainer(
                            accountId, accessToken, fileService.publicUrl(media), caption, false, apiMode)
                    : instagramClient.createImageContainer(
                            accountId, accessToken, fileService.publicUrl(media), caption, false, apiMode);
            instagramClient.awaitContainerReady(containerId, accessToken, apiMode);
            return instagramClient.publishContainer(accountId, accessToken, containerId, apiMode);
        }

        List<String> childIds = new ArrayList<>();
        for (UploadedFile media : mediaFiles) {
            String childId = isVideo(media)
                    ? instagramClient.createVideoContainer(
                            accountId, accessToken, fileService.publicUrl(media), null, true, apiMode)
                    : instagramClient.createImageContainer(
                            accountId, accessToken, fileService.publicUrl(media), null, true, apiMode);
            instagramClient.awaitContainerReady(childId, accessToken, apiMode);
            childIds.add(childId);
        }
        String containerId = instagramClient.createCarouselContainer(
                accountId, accessToken, childIds, caption, apiMode);
        instagramClient.awaitContainerReady(containerId, accessToken, apiMode);
        return instagramClient.publishContainer(accountId, accessToken, containerId, apiMode);
    }

    private InstagramGraphClient.ApiMode apiMode(SocialAccount account) {
        Map<String, Object> metadata = account.getAccountMetadata();
        String configuredMode = metadata == null ? "" : String.valueOf(metadata.getOrDefault("apiMode", ""));
        if (InstagramGraphClient.ApiMode.INSTAGRAM_LOGIN.name().equalsIgnoreCase(configuredMode)) {
            return InstagramGraphClient.ApiMode.INSTAGRAM_LOGIN;
        }
        if (InstagramGraphClient.ApiMode.FACEBOOK_LOGIN.name().equalsIgnoreCase(configuredMode)) {
            return InstagramGraphClient.ApiMode.FACEBOOK_LOGIN;
        }
        String accountType = metadata == null ? "" : String.valueOf(metadata.getOrDefault("accountType", ""));
        return accountType.isBlank()
                ? InstagramGraphClient.ApiMode.FACEBOOK_LOGIN
                : InstagramGraphClient.ApiMode.INSTAGRAM_LOGIN;
    }

    private void validateMedia(UploadedFile media) {
        String mimeType = mimeType(media);
        if (isImage(media) && !"image/jpeg".equals(mimeType)) {
            throw new IllegalStateException("Instagram Content Publishing chỉ hỗ trợ ảnh JPEG; hãy đổi PNG/WebP sang JPG");
        }
        if (isVideo(media) && !List.of("video/mp4", "video/quicktime").contains(mimeType)) {
            throw new IllegalStateException("Instagram chỉ hỗ trợ video MP4 hoặc MOV");
        }
        if (!isImage(media) && !isVideo(media)) {
            throw new IllegalStateException("Instagram không hỗ trợ định dạng media " + mimeType);
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
