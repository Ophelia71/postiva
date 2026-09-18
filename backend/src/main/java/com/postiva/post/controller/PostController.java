package com.postiva.post.controller;

import com.postiva.common.ApiResponse;
import com.postiva.post.dto.GeneratePostRequest;
import com.postiva.post.dto.GeneratePostResponse;
import com.postiva.post.dto.UpdatePostRequest;
import com.postiva.post.service.PostService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/generate")
    public ApiResponse<GeneratePostResponse> generate(@Valid @RequestBody GeneratePostRequest request) {
        return ApiResponse.ok(postService.generate(request));
    }

    @GetMapping("/{postId}")
    public ApiResponse<GeneratePostResponse> getPost(@PathVariable UUID postId) {
        return ApiResponse.ok(postService.getPostResponse(postId));
    }

    @PutMapping("/{postId}")
    public ApiResponse<GeneratePostResponse> update(
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        return ApiResponse.ok(postService.update(postId, request));
    }
}
