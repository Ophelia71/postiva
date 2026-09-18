package com.postiva.social.controller;

import com.postiva.common.ApiResponse;
import com.postiva.social.dto.FacebookBusinessPostRequest;
import com.postiva.social.dto.FacebookBusinessPostResponse;
import com.postiva.social.service.FacebookBusinessPostService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/facebook-business/posts")
public class FacebookBusinessPostController {
    private final FacebookBusinessPostService postService;

    public FacebookBusinessPostController(FacebookBusinessPostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ApiResponse<FacebookBusinessPostResponse> create(@Valid @RequestBody FacebookBusinessPostRequest request) {
        return ApiResponse.ok(postService.create(request));
    }
}
