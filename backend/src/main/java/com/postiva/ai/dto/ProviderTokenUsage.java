package com.postiva.ai.dto;

public record ProviderTokenUsage(
        Integer inputTokens,
        Integer cachedInputTokens,
        Integer cacheWriteInputTokens,
        Integer outputTokens,
        String providerModel,
        String providerRequestId
) {
    public ProviderTokenUsage(Integer inputTokens, Integer cachedInputTokens, Integer outputTokens,
                              String providerModel, String providerRequestId) {
        this(inputTokens, cachedInputTokens, 0, outputTokens, providerModel, providerRequestId);
    }

    public boolean isComplete() {
        return inputTokens != null && inputTokens >= 0
                && cachedInputTokens != null && cachedInputTokens >= 0
                && cacheWriteInputTokens != null && cacheWriteInputTokens >= 0
                && cachedInputTokens + (long) cacheWriteInputTokens <= inputTokens
                && outputTokens != null && outputTokens >= 0;
    }
}
