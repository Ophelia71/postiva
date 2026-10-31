package com.postiva.ai.dto;

import java.util.List;

/** The AI's plan for a week: one short strategy plus one idea per requested day. */
public record WeeklyIdeasResult(String strategy, List<Idea> items) {
    public record Idea(String date, String time, String postType, String topic, String angle, String reason) {
    }
}
