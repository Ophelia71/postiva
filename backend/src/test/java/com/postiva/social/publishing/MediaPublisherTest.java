package com.postiva.social.publishing;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.repository.UploadedFileRepository;
import com.postiva.file.security.MediaAccessSigner;
import com.postiva.file.service.FileService;
import com.postiva.file.storage.LocalFileStorage;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.entity.SocialAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaPublisherTest {
    private static final String VALID_SECRET = "test-media-signing-secret-at-least-32-bytes";
    private static final String PUBLIC_ORIGIN = "https://media.example.test";
    private final InstagramGraphClient instagram = mock(InstagramGraphClient.class);
    private final ThreadsGraphClient threads = mock(ThreadsGraphClient.class);

    @ParameterizedTest
    @EnumSource(value = Platform.class, names = {"INSTAGRAM", "THREADS"})
    void missingSigningKeyFailsBeforeAnyMetaCall(Platform platform) {
        PlatformPublisher publisher = publisher(platform, files("", PUBLIC_ORIGIN));

        var failure = assertThrows(PublishPreparationException.class,
                () -> publisher.publish(post(), account(), "token", List.of(media())));

        assertTrue(failure.getMessage().contains("POSTIVA_MEDIA_SIGNING_SECRET"));
        verifyNoInteractions(instagram, threads);
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class, names = {"INSTAGRAM", "THREADS"})
    void localhostMediaFailsBeforeAnyMetaCall(Platform platform) {
        PlatformPublisher publisher = publisher(platform, files(VALID_SECRET, "http://localhost:8080"));

        var failure = assertThrows(PublishPreparationException.class,
                () -> publisher.publish(post(), account(), "token", List.of(media())));

        assertTrue(failure.getMessage().contains("localhost"));
        verifyNoInteractions(instagram, threads);
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class, names = {"INSTAGRAM", "THREADS"})
    void validatesAllCarouselUrlsBeforeCreatingContainers(Platform platform) {
        FileService files = mock(FileService.class);
        UploadedFile first = media();
        UploadedFile second = media();
        when(files.publicUrl(first)).thenReturn(PUBLIC_ORIGIN + "/first.jpg");
        when(files.publicUrl(second)).thenThrow(new IllegalStateException("Invalid second media URL"));

        assertThrows(PublishPreparationException.class,
                () -> publisher(platform, files).publish(post(), account(), "token", List.of(first, second)));

        verifyNoInteractions(instagram, threads);
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class, names = {"INSTAGRAM", "THREADS"})
    void rejectedContainerIsDefinitelyNotPublished(Platform platform) {
        stubContainer();
        var cause = new IllegalStateException("Media processing failed");
        doThrow(cause).when(instagram).awaitContainerReady(anyString(), anyString(), any());
        doThrow(cause).when(threads).awaitContainerReady(anyString(), anyString());

        var failure = assertThrows(PublishPreparationException.class, () ->
                publisher(platform, files(VALID_SECRET, PUBLIC_ORIGIN))
                        .publish(post(), account(), "token", List.of(media())));

        assertSame(cause, failure.getCause());
        verify(instagram, never()).publishContainer(anyString(), anyString(), anyString(), any());
        verify(threads, never()).publishContainer(anyString(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class, names = {"INSTAGRAM", "THREADS"})
    void finalPublishTimeoutIsNotMisclassifiedAsPreparationFailure(Platform platform) {
        stubContainer();
        RuntimeException timeout = new RuntimeException("Publish response lost", new TimeoutException());
        when(instagram.publishContainer(anyString(), anyString(), anyString(), any())).thenThrow(timeout);
        when(threads.publishContainer(anyString(), anyString())).thenThrow(timeout);

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                publisher(platform, files(VALID_SECRET, PUBLIC_ORIGIN))
                        .publish(post(), account(), "token", List.of(media())));

        assertSame(timeout, failure);
        assertFalse(failure instanceof PublishPreparationException);
    }

    @ParameterizedTest
    @EnumSource(value = Platform.class, names = {"INSTAGRAM", "THREADS"})
    void validMediaStillPublishesExactlyOnceWithSignedUrl(Platform platform) {
        stubContainer();
        when(instagram.publishContainer(anyString(), anyString(), eq("container"), any())).thenReturn("published");
        when(threads.publishContainer(anyString(), eq("container"))).thenReturn("published");

        String result = publisher(platform, files(VALID_SECRET, PUBLIC_ORIGIN))
                .publish(post(), account(), "token", List.of(media()));

        assertEquals("published", result);
        if (platform == Platform.INSTAGRAM) {
            verify(instagram).createImageContainer(eq("account"), eq("token"),
                    startsWith(PUBLIC_ORIGIN + "/api/files/public/"), eq("Test caption"), eq(false), any());
            verify(instagram, times(1)).publishContainer(eq("account"), eq("token"), eq("container"), any());
        } else {
            verify(threads).createMediaContainer(eq("token"), eq("Test content"), eq("IMAGE"),
                    startsWith(PUBLIC_ORIGIN + "/api/files/public/"), eq(false));
            verify(threads, times(1)).publishContainer("token", "container");
        }
    }

    @Test
    void threadsTextOnlyDoesNotRequireMediaSigningConfiguration() {
        when(threads.createTextContainer("token", "Test content")).thenReturn("container");
        when(threads.publishContainer("token", "container")).thenReturn("published");

        String result = publisher(Platform.THREADS, files("", ""))
                .publish(post(), account(), "token", List.of());

        assertEquals("published", result);
        verify(threads, times(1)).publishContainer("token", "container");
        verifyNoInteractions(instagram);
    }

    private PlatformPublisher publisher(Platform platform, FileService files) {
        return platform == Platform.INSTAGRAM
                ? new InstagramPublisher(instagram, files) : new ThreadsPublisher(threads, files);
    }

    private FileService files(String secret, String publicOrigin) {
        return new FileService(mock(UploadedFileRepository.class), mock(LocalFileStorage.class),
                mock(CurrentUserService.class), new MediaAccessSigner(secret, 3600), publicOrigin);
    }

    private void stubContainer() {
        when(instagram.createImageContainer(anyString(), anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn("container");
        when(threads.createMediaContainer(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("container");
    }

    private GeneratedPost post() {
        GeneratedPost post = new GeneratedPost();
        post.setPlatformContents(Map.of(
                "INSTAGRAM", Map.of("caption", "Test caption"),
                "THREADS", Map.of("content", "Test content")));
        return post;
    }

    private SocialAccount account() {
        SocialAccount account = new SocialAccount();
        account.setPageId("account");
        return account;
    }

    private UploadedFile media() {
        UploadedFile media = new UploadedFile();
        ReflectionTestUtils.setField(media, "id", UUID.randomUUID());
        media.setMimeType("image/jpeg");
        media.setStorageProvider("LOCAL");
        media.setFileUrl("/api/files/" + media.getId());
        return media;
    }
}
