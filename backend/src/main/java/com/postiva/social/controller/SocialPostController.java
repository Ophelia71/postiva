package com.postiva.social.controller;

import com.postiva.common.ApiResponse;
import com.postiva.social.dto.SocialPostRequest;
import com.postiva.social.dto.SocialPostResponse;
import com.postiva.social.service.SocialPostService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social-posts")
public class SocialPostController {
    private final SocialPostService socialPostService;

    public SocialPostController(SocialPostService socialPostService) {
        this.socialPostService = socialPostService;
    }

    @PostMapping
    public ApiResponse<SocialPostResponse> create(@Valid @RequestBody SocialPostRequest request) {
        return ApiResponse.ok(socialPostService.create(request));
    }
}
