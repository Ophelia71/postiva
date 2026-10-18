SET LOCAL lock_timeout = '5s';

ALTER TABLE ai_provider_costs
    ADD COLUMN cache_write_input_rate_usd_per_million NUMERIC(16,10) NOT NULL DEFAULT 0
        CHECK (cache_write_input_rate_usd_per_million >= 0),
    ADD COLUMN cache_write_input_tokens INTEGER CHECK (cache_write_input_tokens >= 0);

ALTER TABLE ai_provider_costs
    ADD CONSTRAINT ck_ai_provider_costs_prompt_token_breakdown
        CHECK (input_tokens IS NULL OR
            COALESCE(cached_input_tokens, 0) + COALESCE(cache_write_input_tokens, 0) <= input_tokens);
