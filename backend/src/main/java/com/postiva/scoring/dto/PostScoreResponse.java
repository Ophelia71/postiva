package com.postiva.scoring.dto;

import java.util.List;
import java.util.UUID;

public record PostScoreResponse(
        UUID postId,
        int score,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions
) {
}

