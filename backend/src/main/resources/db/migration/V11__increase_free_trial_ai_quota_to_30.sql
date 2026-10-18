-- Increase current Free trial periods while preserving usage and period dates.
-- Periods already above 30 (for example, custom pilot quotas) are left unchanged.
UPDATE subscription_periods
SET ai_limit = 30
WHERE plan_code = 'FREE'
  AND ends_at > CURRENT_TIMESTAMP
  AND ai_limit < 30;
