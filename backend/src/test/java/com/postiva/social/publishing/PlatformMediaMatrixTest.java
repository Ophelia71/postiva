package com.postiva.social.publishing;

import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.entity.SocialAccount;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What each platform does with no image, one image, many images, a video, or too many files. Meta is faked. */
class PlatformMediaMatrixTest {
    private final FacebookGraphClient facebook = mock(FacebookGraphClient.class);
    private final InstagramGraphClient instagram = mock(InstagramGraphClient.class);
    private final ThreadsGraphClient threads = mock(ThreadsGraphClient.class);
    private final FileService files = mock(FileService.class);

    private final FacebookPublisher facebookPublisher = new FacebookPublisher(facebook, files);
    private final InstagramPublisher instagramPublisher = new InstagramPublisher(instagram, files);
    private final ThreadsPublisher threadsPublisher = new ThreadsPublisher(threads, files);

    PlatformMediaMatrixTest() {
        when(files.publicUrl(any())).thenReturn("https://media.example.test/file");
        when(files.resource(any())).thenReturn(new FileSystemResource("file"));
        when(facebook.publishText(anyString(), anyString(), anyString())).thenReturn("fb-text");
        when(facebook.publishPhoto(anyString(), anyString(), anyString(), any())).thenReturn("fb-photo");
        when(facebook.publishPhotos(anyString(), anyString(), anyString(), anyList())).thenReturn("fb-photos");
        when(facebook.publishVideo(anyString(), anyString(), anyString(), any())).thenReturn("fb-video");
        when(instagram.createImageContainer(anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .thenReturn("ig-container");
        when(instagram.createVideoContainer(anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .thenReturn("ig-video-container");
        when(instagram.createCarouselContainer(anyString(), anyString(), anyList(), anyString(), any()))
                .thenReturn("ig-carousel");
        when(instagram.publishContainer(anyString(), anyString(), anyString(), any())).thenReturn("ig-post");
        when(threads.createTextContainer(anyString(), anyString())).thenReturn("th-text");
        when(threads.createMediaContainer(anyString(), any(), anyString(), anyString(), anyBoolean()))
                .thenReturn("th-container");
        when(threads.createCarouselContainer(anyString(), anyString(), anyList())).thenReturn("th-carousel");
        when(threads.publishContainer(anyString(), anyString())).thenReturn("th-post");
    }

    // ---- Facebook

    @Test
    void facebookTextOnlyPostsText() {
        assertEquals("fb-text", facebookPublisher.publish(post(), account(), "t", List.of()));
        verify(facebook).publishText(anyString(), anyString(), anyString());
    }

    @Test
    void facebookOneImagePostsOnePhoto() {
        assertEquals("fb-photo", facebookPublisher.publish(post(), account(), "t", images(1)));
        verify(facebook).publishPhoto(anyString(), anyString(), anyString(), any());
        verify(facebook, never()).publishPhotos(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void facebookSeveralImagesPostOneMultiPhotoPost() {
        assertEquals("fb-photos", facebookPublisher.publish(post(), account(), "t", images(4)));
        verify(facebook, times(1)).publishPhotos(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void facebookAcceptsTenImagesAndRejectsEleven() {
        assertEquals("fb-photos", facebookPublisher.publish(post(), account(), "t", images(10)));
        var failure = assertThrows(IllegalStateException.class,
                () -> facebookPublisher.publish(post(), account(), "t", images(11)));
        assertTrue(failure.getMessage().contains("10 ảnh"));
    }

    @Test
    void facebookOneVideoPostsVideo() {
        assertEquals("fb-video", facebookPublisher.publish(post(), account(), "t", List.of(media("video/mp4"))));
    }

    @Test
    void facebookRejectsMixingImagesAndVideo() {
        var mixed = List.of(media("image/jpeg"), media("video/mp4"));
        assertThrows(IllegalStateException.class, () -> facebookPublisher.publish(post(), account(), "t", mixed));
        verifyNoInteractions(facebook);
    }

    // ---- Instagram

    @Test
    void instagramNeedsAtLeastOneMedia() {
        var failure = assertThrows(PublishPreparationException.class,
                () -> instagramPublisher.publish(post(), account(), "t", List.of()));
        assertTrue(failure.getMessage().contains("ít nhất một ảnh"));
        verifyNoInteractions(instagram);
    }

    @Test
    void instagramOneImageUsesASingleContainerNotACarousel() {
        assertEquals("ig-post", instagramPublisher.publish(post(), account(), "t", images(1)));
        verify(instagram, times(1)).createImageContainer(anyString(), anyString(), anyString(), anyString(), anyBoolean(), any());
        verify(instagram, never()).createCarouselContainer(anyString(), anyString(), anyList(), anyString(), any());
    }

    @Test
    void instagramSeveralImagesBuildOneCarouselWithAChildPerImage() {
        assertEquals("ig-post", instagramPublisher.publish(post(), account(), "t", images(3)));
        // Children carry no caption (it belongs to the carousel) and are flagged as carousel items.
        verify(instagram, times(3)).createImageContainer(anyString(), anyString(), anyString(), isNull(), org.mockito.ArgumentMatchers.eq(true), any());
        verify(instagram, times(1)).createCarouselContainer(anyString(), anyString(), anyList(), anyString(), any());
    }

    @Test
    void instagramAcceptsTenMediaAndRejectsEleven() {
        assertEquals("ig-post", instagramPublisher.publish(post(), account(), "t", images(10)));
        var failure = assertThrows(PublishPreparationException.class,
                () -> instagramPublisher.publish(post(), account(), "t", images(11)));
        assertTrue(failure.getMessage().contains("10"));
    }

    @Test
    void instagramRejectsPngAndKeepsJpegOnly() {
        var failure = assertThrows(PublishPreparationException.class,
                () -> instagramPublisher.publish(post(), account(), "t", List.of(media("image/png"))));
        assertTrue(failure.getMessage().contains("JPEG"));
        verify(instagram, never()).publishContainer(anyString(), anyString(), anyString(), any());
    }

    @Test
    void instagramOneVideoUsesAVideoContainer() {
        assertEquals("ig-post", instagramPublisher.publish(post(), account(), "t", List.of(media("video/mp4"))));
        verify(instagram).createVideoContainer(anyString(), anyString(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void instagramCaptionOver2200CharactersIsRejectedBeforeAnyCall() {
        GeneratedPost longPost = post();
        longPost.setPlatformContents(Map.of("INSTAGRAM", Map.of("caption", "a".repeat(2201))));
        assertThrows(PublishPreparationException.class,
                () -> instagramPublisher.publish(longPost, account(), "t", images(1)));
        verifyNoInteractions(instagram);
    }

    @Test
    void instagramCreatesEveryCarouselChildBeforeWaitingOnAny() {
        instagramPublisher.publish(post(), account(), "t", images(3));

        var order = org.mockito.Mockito.inOrder(instagram);
        order.verify(instagram, times(3)).createImageContainer(anyString(), anyString(), anyString(), isNull(), anyBoolean(), any());
        order.verify(instagram, org.mockito.Mockito.atLeast(3)).awaitContainerReady(anyString(), anyString(), any());
    }

    @Test
    void threadsCreatesEveryCarouselChildBeforeWaitingOnAny() {
        threadsPublisher.publish(post(), account(), "t", images(3));

        var order = org.mockito.Mockito.inOrder(threads);
        order.verify(threads, times(3)).createMediaContainer(anyString(), isNull(), anyString(), anyString(), anyBoolean());
        order.verify(threads, org.mockito.Mockito.atLeast(3)).awaitContainerReady(anyString(), anyString());
    }

    // ---- Threads

    @Test
    void threadsTextOnlyUsesATextContainer() {
        assertEquals("th-post", threadsPublisher.publish(post(), account(), "t", List.of()));
        verify(threads).createTextContainer(anyString(), anyString());
    }

    @Test
    void threadsOneImageUsesASingleMediaContainer() {
        assertEquals("th-post", threadsPublisher.publish(post(), account(), "t", images(1)));
        verify(threads, times(1)).createMediaContainer(anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(false));
        verify(threads, never()).createCarouselContainer(anyString(), anyString(), anyList());
    }

    @Test
    void threadsSeveralImagesBuildOneCarousel() {
        assertEquals("th-post", threadsPublisher.publish(post(), account(), "t", images(3)));
        verify(threads, times(3)).createMediaContainer(anyString(), isNull(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(true));
        verify(threads, times(1)).createCarouselContainer(anyString(), anyString(), anyList());
    }

    @Test
    void threadsAcceptsTwentyMediaAndRejectsTwentyOne() {
        assertEquals("th-post", threadsPublisher.publish(post(), account(), "t", images(20)));
        var failure = assertThrows(PublishPreparationException.class,
                () -> threadsPublisher.publish(post(), account(), "t", images(21)));
        assertTrue(failure.getMessage().contains("20"));
    }

    @Test
    void threadsTextOver500CharactersIsRejectedBeforeAnyCall() {
        GeneratedPost longPost = post();
        longPost.setPlatformContents(Map.of("THREADS", Map.of("content", "a".repeat(501))));
        assertThrows(PublishPreparationException.class,
                () -> threadsPublisher.publish(longPost, account(), "t", List.of()));
        verifyNoInteractions(threads);
    }

    // ---- Content per platform

    @Test
    void aPlatformWithoutItsOwnContentIsRejected() {
        GeneratedPost onlyInstagram = post();
        onlyInstagram.setPlatformContents(Map.of("INSTAGRAM", Map.of("caption", "chỉ có Instagram")));
        var failure = assertThrows(IllegalStateException.class,
                () -> facebookPublisher.publish(onlyInstagram, account(), "t", List.of()));
        assertTrue(failure.getMessage().contains("Facebook"));
    }

    private GeneratedPost post() {
        GeneratedPost post = new GeneratedPost();
        post.setPlatformContents(Map.of(
                "FACEBOOK", Map.of("content", "Nội dung Facebook"),
                "INSTAGRAM", Map.of("caption", "Caption Instagram"),
                "THREADS", Map.of("content", "Nội dung Threads")));
        return post;
    }

    private SocialAccount account() {
        SocialAccount account = new SocialAccount();
        account.setPageId("account");
        return account;
    }

    private List<UploadedFile> images(int count) {
        List<UploadedFile> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(media("image/jpeg"));
        }
        return list;
    }

    private UploadedFile media(String mimeType) {
        UploadedFile media = new UploadedFile();
        ReflectionTestUtils.setField(media, "id", UUID.randomUUID());
        media.setMimeType(mimeType);
        media.setStorageProvider("LOCAL");
        media.setFileUrl("/api/files/" + media.getId());
        return media;
    }
}
