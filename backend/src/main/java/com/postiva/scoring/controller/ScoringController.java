package com.postiva.scoring.controller;

import com.postiva.common.ApiResponse;
import com.postiva.scoring.dto.PostScoreResponse;
import com.postiva.scoring.service.ScoringService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts/{postId}/score")
public class ScoringController {
    private final ScoringService scoringService;

    public ScoringController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping
    public ApiResponse<PostScoreResponse> score(@PathVariable UUID postId) {
        return ApiResponse.ok(scoringService.score(postId));
    }
}
