package com.postiva.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.business.service.ShopProfileService;
import com.postiva.ai.service.OpenAiService;
import com.postiva.common.PostStatus;
import com.postiva.config.OpenAiProperties;
import com.postiva.file.service.FileService;
import com.postiva.post.dto.UpdatePostRequest;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.template.service.TemplateService;
import com.postiva.usage.AiUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Editing the images of a weekly plan draft goes through the normal post update. */
class PostServiceMediaTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID briefId = UUID.randomUUID();
    private final GeneratedPostRepository posts = mock(GeneratedPostRepository.class);
    private final FileService files = mock(FileService.class);
    private final PostService service;

    PostServiceMediaTest() {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireCurrentUserId()).thenReturn(userId);
        service = new PostService(mock(ProductBriefRepository.class), posts, mock(TemplateService.class),
                mock(OpenAiService.class), currentUser, files, mock(AiUsageService.class), mock(OpenAiProperties.class),
                new ObjectMapper(), mock(PlatformTransactionManager.class), mock(ShopProfileService.class));
        when(posts.save(any(GeneratedPost.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void draftFor(String platform) {
        GeneratedPost post = new GeneratedPost();
        ReflectionTestUtils.setField(post, "id", postId);
        post.setUserId(userId);
        post.setBriefId(briefId);
        post.setStatus(PostStatus.DRAFT);
        post.setPlatformContents(Map.of(platform, Map.of("content", "Xin chào")));
        when(posts.findByIdAndUserId(postId, userId)).thenReturn(Optional.of(post));
    }

    private UpdatePostRequest withMedia(List<UUID> mediaIds) {
        return new UpdatePostRequest(null, null, List.of(), null, mediaIds);
    }

    @Test
    void newFilesReplaceTheDraftMediaAndSetTheCover() {
        draftFor("FACEBOOK");
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(files.replacePostMedia(ids, userId, briefId, postId)).thenReturn("/api/files/" + ids.get(0));

        var response = service.update(postId, withMedia(ids));

        assertEquals("/api/files/" + ids.get(0), response.imageUrl());
        verify(files).replacePostMedia(ids, userId, briefId, postId);
    }

    @Test
    void removingEveryFileFromAFacebookDraftClearsTheCover() {
        draftFor("FACEBOOK");
        when(files.replacePostMedia(List.of(), userId, briefId, postId)).thenReturn(null);

        var response = service.update(postId, withMedia(List.of()));

        assertNull(response.imageUrl());
    }

    @Test
    void instagramDraftCannotBeLeftWithoutMedia() {
        draftFor("INSTAGRAM");

        assertThrows(ResponseStatusException.class, () -> service.update(postId, withMedia(List.of())));

        verify(files, never()).replacePostMedia(any(), any(), any(), any());
    }

    @Test
    void moreThanTenFilesAreRefusedOnFacebookAndInstagram() {
        draftFor("FACEBOOK");
        List<UUID> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> UUID.randomUUID()).toList();

        assertThrows(ResponseStatusException.class, () -> service.update(postId, withMedia(eleven)));
    }

    @Test
    void leavingMediaOutOfTheRequestKeepsTheCurrentFiles() {
        draftFor("FACEBOOK");

        service.update(postId, withMedia(null));

        verify(files, never()).replacePostMedia(any(), any(), any(), any());
    }
}
