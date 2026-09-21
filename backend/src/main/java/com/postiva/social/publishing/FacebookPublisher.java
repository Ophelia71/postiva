package com.postiva.social.publishing;

import com.postiva.common.Platform;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.entity.SocialAccount;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FacebookPublisher implements PlatformPublisher {
    private static final int MAX_PHOTOS = 10;

    private final FacebookGraphClient facebookClient;
    private final FileService fileService;

    public FacebookPublisher(FacebookGraphClient facebookClient, FileService fileService) {
        this.facebookClient = facebookClient;
        this.fileService = fileService;
    }

    @Override
    public Platform platform() {
        return Platform.FACEBOOK;
    }

    @Override
    public String publish(GeneratedPost post,
                          SocialAccount account,
                          String accessToken,
                          List<UploadedFile> mediaFiles) {
        String message = PlatformPostContent.require(post, Platform.FACEBOOK);
        String pageId = account.getPageId();

        if (mediaFiles.size() > 1 && mediaFiles.stream().allMatch(this::isImage)) {
            if (mediaFiles.size() > MAX_PHOTOS) {
                throw new IllegalStateException("Facebook hỗ trợ tối đa 10 ảnh trong một bài đăng");
            }
            return facebookClient.publishPhotos(
                    pageId,
                    accessToken,
                    message,
                    mediaFiles.stream().map(media -> (Resource) fileService.resource(media)).toList()
            );
        }
        if (mediaFiles.size() == 1 && isVideo(mediaFiles.get(0))) {
            return facebookClient.publishVideo(
                    pageId, accessToken, message, fileService.resource(mediaFiles.get(0)));
        }
        if (mediaFiles.size() == 1 && isImage(mediaFiles.get(0))) {
            return facebookClient.publishPhoto(
                    pageId, accessToken, message, fileService.resource(mediaFiles.get(0)));
        }
        if (!mediaFiles.isEmpty()) {
            throw new IllegalStateException(
                    "Facebook chỉ đăng được nhiều ảnh hoặc một video; không thể trộn ảnh và video trong cùng bài"
            );
        }

        String externalUrl = post.getImageUrl();
        if (isHttpUrl(externalUrl) && "VIDEO".equals(PlatformPostContent.mediaType(post, Platform.FACEBOOK))) {
            return facebookClient.publishVideoUrl(pageId, accessToken, message, externalUrl);
        }
        if (isHttpUrl(externalUrl) && "IMAGE".equals(PlatformPostContent.mediaType(post, Platform.FACEBOOK))) {
            return facebookClient.publishPhotoUrl(pageId, accessToken, message, externalUrl);
        }
        return facebookClient.publishText(pageId, accessToken, message);
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

    private boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }
}
