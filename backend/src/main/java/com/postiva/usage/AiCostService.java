package com.postiva.usage;

import com.postiva.ai.dto.ProviderTokenUsage;
import com.postiva.config.AiCostProperties;
import com.postiva.config.OpenAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AiCostService {
    private static final Logger log = LoggerFactory.getLogger(AiCostService.class);
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private final JdbcTemplate jdbc;
    private final AiCostProperties properties;
    private final OpenAiProperties openAiProperties;

    public AiCostService(JdbcTemplate jdbc, AiCostProperties properties, OpenAiProperties openAiProperties) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.openAiProperties = openAiProperties;
    }

    public void ensureEnabled() {
        Boolean enabled = jdbc.queryForObject("SELECT enabled FROM ai_runtime_control WHERE id = 1", Boolean.class);
        if (!properties.isEnabled() || !Boolean.TRUE.equals(enabled)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI đang được tạm dừng bởi quản trị viên.");
        }
    }

    /** Reserve a conservative worst-case estimate in both UTC budget windows before contacting the provider. */
    @Transactional
    public BigDecimal reserveBudget(Instant createdAt) {
        LocalDate day = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate month = day.withDayOfMonth(1);
        BigDecimal maxInput = tokenCost(properties.getMaxInputTokens(), highestInputRate());
        BigDecimal maxOutput = tokenCost(properties.getMaxOutputTokens(), properties.getOutputUsdPerMillionTokens());
        BigDecimal reserve = maxInput.add(maxOutput).multiply(properties.getReservationMultiplier()).setScale(10, RoundingMode.CEILING);

        ensureWindows(day, month);
        List<BudgetWindow> windows = lockWindows(day, month);
        for (BudgetWindow window : windows) {
            BigDecimal proposed = window.spent().max(window.actualProvider()).add(window.reserved()).add(reserve);
            if (proposed.compareTo(window.budget()) > 0) {
                String interval = "DAILY".equals(window.type()) ? "hôm nay" : "tháng này";
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Dịch vụ AI đã chạm ngân sách " + interval + " và đang tạm dừng để bảo vệ chi phí.");
            }
        }
        for (BudgetWindow window : windows) {
            jdbc.update("UPDATE ai_cost_budget_windows SET reserved_usd = reserved_usd + ? WHERE window_type = ? AND window_start = ?",
                    reserve, window.type(), window.start());
        }
        checkWarnings(day, month);
        return reserve;
    }

    @Transactional
    public void createReservation(UUID requestId, Instant createdAt, String model, BigDecimal reservedUsd) {
        LocalDate day = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
        jdbc.update("""
                INSERT INTO ai_provider_costs (request_id, usage_day, usage_month, model, input_rate_usd_per_million,
                    cached_input_rate_usd_per_million, cache_write_input_rate_usd_per_million,
                    output_rate_usd_per_million, status, reserved_usd)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?)
                """, requestId, day, day.withDayOfMonth(1), model, properties.getInputUsdPerMillionTokens(),
                properties.getCachedInputUsdPerMillionTokens(), properties.getCacheWriteInputUsdPerMillionTokens(),
                properties.getOutputUsdPerMillionTokens(), reservedUsd);
    }

    @Transactional
    public void recordUsage(UUID requestId, ProviderTokenUsage providerUsage) {
        List<CostReservation> rows = jdbc.query("SELECT * FROM ai_provider_costs WHERE request_id = ? FOR UPDATE",
                (rs, row) -> reservation(rs), requestId);
        if (rows.isEmpty()) return;
        CostReservation row = rows.getFirst();
        if (!"RESERVED".equals(row.status()) && !"UNKNOWN".equals(row.status())) return;
        if (providerUsage == null || !providerUsage.isComplete()) {
            jdbc.update("UPDATE ai_provider_costs SET status = 'UNKNOWN', provider_model = ?, provider_request_id = ? WHERE request_id = ?",
                    providerUsage == null ? null : providerUsage.providerModel(),
                    providerUsage == null ? null : providerUsage.providerRequestId(), requestId);
            log.warn("AI provider usage is unknown for request {}; keep reserved budget until reconciled", requestId);
            return;
        }

        BigDecimal actual = calculateCost(providerUsage, row.inputRate(), row.cachedInputRate(),
                row.cacheWriteInputRate(), row.outputRate());
        jdbc.update("""
                UPDATE ai_cost_budget_windows SET reserved_usd = greatest(0, reserved_usd - ?), spent_usd = spent_usd + ?
                WHERE (window_type = 'DAILY' AND window_start = ?) OR (window_type = 'MONTHLY' AND window_start = ?)
                """, row.reservedUsd(), actual, row.usageDay(), row.usageMonth());
        jdbc.update("""
                UPDATE ai_provider_costs SET status = 'SETTLED', input_tokens = ?, cached_input_tokens = ?,
                    cache_write_input_tokens = ?, output_tokens = ?,
                    provider_model = ?, provider_request_id = ?, estimated_cost_usd = ?, settled_at = clock_timestamp()
                WHERE request_id = ?
                """, providerUsage.inputTokens(), providerUsage.cachedInputTokens(), providerUsage.cacheWriteInputTokens(),
                providerUsage.outputTokens(),
                providerUsage.providerModel(), providerUsage.providerRequestId(), actual, requestId);
        checkWarnings(row.usageDay(), row.usageMonth());
    }

    /** Definite provider 4xx failures release the reserved spend; timeouts/5xx keep it as an unknown charge. */
    @Transactional
    public void finishFailure(UUID requestId, boolean uncertain) {
        List<CostReservation> rows = jdbc.query("SELECT * FROM ai_provider_costs WHERE request_id = ? FOR UPDATE",
                (rs, row) -> reservation(rs), requestId);
        if (rows.isEmpty()) return;
        CostReservation row = rows.getFirst();
        if (!"RESERVED".equals(row.status())) return;
        if (uncertain) {
            jdbc.update("UPDATE ai_provider_costs SET status = 'UNKNOWN' WHERE request_id = ?", requestId);
            log.warn("AI provider result is uncertain for request {}; keep reserved budget until reconciled", requestId);
            return;
        }
        jdbc.update("""
                UPDATE ai_cost_budget_windows SET reserved_usd = greatest(0, reserved_usd - ?)
                WHERE (window_type = 'DAILY' AND window_start = ?) OR (window_type = 'MONTHLY' AND window_start = ?)
                """, row.reservedUsd(), row.usageDay(), row.usageMonth());
        jdbc.update("UPDATE ai_provider_costs SET status = 'RELEASED', reserved_usd = 0, settled_at = clock_timestamp() WHERE request_id = ?", requestId);
    }

    @Transactional(readOnly = true)
    public BudgetStatus status(Instant now) {
        LocalDate day = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate month = day.withDayOfMonth(1);
        BudgetWindow daily = readWindow("DAILY", day, properties.getDailyBudgetUsd());
        BudgetWindow monthly = readWindow("MONTHLY", month, properties.getMonthlyBudgetUsd());
        Boolean dbEnabled = jdbc.queryForObject("SELECT enabled FROM ai_runtime_control WHERE id = 1", Boolean.class);
        Long unknown = jdbc.queryForObject("SELECT count(*) FROM ai_provider_costs WHERE status = 'UNKNOWN'", Long.class);
        Long settled = jdbc.queryForObject("SELECT count(*) FROM ai_provider_costs WHERE status = 'SETTLED'", Long.class);
        BigDecimal nextReserve = tokenCost(properties.getMaxInputTokens(), highestInputRate())
                .add(tokenCost(properties.getMaxOutputTokens(), properties.getOutputUsdPerMillionTokens()))
                .multiply(properties.getReservationMultiplier()).setScale(10, RoundingMode.CEILING);
        boolean enabled = properties.isEnabled() && Boolean.TRUE.equals(dbEnabled);
        boolean budgetAllowsRequest = daily.spent().max(daily.actualProvider()).add(daily.reserved()).add(nextReserve).compareTo(daily.budget()) <= 0
                && monthly.spent().max(monthly.actualProvider()).add(monthly.reserved()).add(nextReserve).compareTo(monthly.budget()) <= 0;
        return new BudgetStatus(enabled, enabled && budgetAllowsRequest, Boolean.TRUE.equals(dbEnabled),
                properties.isEnabled(), summarize(daily), summarize(monthly), unknown == null ? 0 : unknown,
                settled == null ? 0 : settled, properties.isProviderReconciliationEnabled()
                ? "OPENAI_ORGANIZATION_COSTS_AND_PER_REQUEST_TOKEN_ESTIMATES" : "ESTIMATED_FROM_PROVIDER_TOKEN_USAGE",
                properties.isProviderReconciliationEnabled(), openAiProperties.model(),
                properties.getInputUsdPerMillionTokens(), properties.getCachedInputUsdPerMillionTokens(),
                properties.getCacheWriteInputUsdPerMillionTokens(), properties.getOutputUsdPerMillionTokens());
    }

    @Transactional
    public void setEnabled(boolean enabled, UUID adminId, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do bật/tắt AI phải có từ 1 đến 1000 ký tự.");
        }
        jdbc.update("UPDATE ai_runtime_control SET enabled = ?, changed_at = clock_timestamp(), changed_by = ?, reason = ? WHERE id = 1",
                enabled, adminId, normalizedReason);
        jdbc.update("INSERT INTO ai_runtime_control_audit (admin_user_id, enabled, reason) VALUES (?, ?, ?)", adminId, enabled, normalizedReason);
    }

    private void ensureWindows(LocalDate day, LocalDate month) {
        jdbc.update("INSERT INTO ai_cost_budget_windows (window_type, window_start, budget_usd) VALUES ('DAILY', ?, ?) ON CONFLICT (window_type, window_start) DO UPDATE SET budget_usd = EXCLUDED.budget_usd",
                day, properties.getDailyBudgetUsd());
        jdbc.update("INSERT INTO ai_cost_budget_windows (window_type, window_start, budget_usd) VALUES ('MONTHLY', ?, ?) ON CONFLICT (window_type, window_start) DO UPDATE SET budget_usd = EXCLUDED.budget_usd",
                month, properties.getMonthlyBudgetUsd());
    }

    private List<BudgetWindow> lockWindows(LocalDate day, LocalDate month) {
        return jdbc.query("SELECT * FROM ai_cost_budget_windows WHERE (window_type = 'DAILY' AND window_start = ?) OR (window_type = 'MONTHLY' AND window_start = ?) ORDER BY window_type FOR UPDATE",
                (rs, row) -> budgetWindow(rs), day, month);
    }

    private BudgetWindow readWindow(String type, LocalDate start, BigDecimal configuredBudget) {
        return jdbc.query("SELECT * FROM ai_cost_budget_windows WHERE window_type = ? AND window_start = ?",
                        (rs, row) -> budgetWindow(rs), type, start)
                .stream().findFirst().map(window -> new BudgetWindow(type, start, configuredBudget, window.spent(),
                        window.actualProvider(), window.reserved(), window.warned(), window.actualReconciledAt()))
                .orElse(new BudgetWindow(type, start, configuredBudget, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, false, null));
    }

    private void checkWarnings(LocalDate day, LocalDate month) {
        List<BudgetWindow> windows = lockWindows(day, month);
        for (BudgetWindow window : windows) {
            BigDecimal threshold = window.budget().multiply(BigDecimal.valueOf(properties.getWarningPercent())).movePointLeft(2);
            if (window.spent().max(window.actualProvider()).add(window.reserved()).compareTo(threshold) >= 0 && !window.warned()) {
                List<BudgetWindow> marked = jdbc.query("""
                        UPDATE ai_cost_budget_windows SET warning_sent_at = clock_timestamp()
                        WHERE window_type = ? AND window_start = ? AND warning_sent_at IS NULL
                        RETURNING *
                        """, (rs, row) -> budgetWindow(rs), window.type(), window.start());
                if (!marked.isEmpty()) log.warn("AI {} spend reached warning threshold: spendUsd={}, reservedUsd={}, budgetUsd={}, start={}",
                        window.type(), window.spent().max(window.actualProvider()), window.reserved(), window.budget(), window.start());
            }
        }
    }

    static BigDecimal calculateCost(ProviderTokenUsage usage, BigDecimal inputRate, BigDecimal cachedInputRate,
                                    BigDecimal cacheWriteInputRate, BigDecimal outputRate) {
        int standardTokens = usage.inputTokens() - usage.cachedInputTokens() - usage.cacheWriteInputTokens();
        return tokenCost(standardTokens, inputRate)
                .add(tokenCost(usage.cachedInputTokens(), cachedInputRate))
                .add(tokenCost(usage.cacheWriteInputTokens(), cacheWriteInputRate))
                .add(tokenCost(usage.outputTokens(), outputRate))
                .setScale(10, RoundingMode.CEILING);
    }

    private static BigDecimal tokenCost(int tokens, BigDecimal ratePerMillion) {
        return BigDecimal.valueOf(tokens).multiply(ratePerMillion).divide(MILLION, 10, RoundingMode.CEILING);
    }

    private BigDecimal highestInputRate() {
        return properties.getInputUsdPerMillionTokens()
                .max(properties.getCachedInputUsdPerMillionTokens())
                .max(properties.getCacheWriteInputUsdPerMillionTokens());
    }

    private BudgetSummary summarize(BudgetWindow window) {
        BigDecimal total = window.spent().max(window.actualProvider()).add(window.reserved());
        BigDecimal percent = window.budget().signum() == 0 ? BigDecimal.ZERO
                : total.multiply(BigDecimal.valueOf(100)).divide(window.budget(), 2, RoundingMode.HALF_UP);
        return new BudgetSummary(window.type(), window.start(), window.budget(), window.spent(), window.actualProvider(),
                window.reserved(), total, percent, window.warned(), window.actualReconciledAt());
    }

    private BudgetWindow budgetWindow(ResultSet rs) throws SQLException {
        Timestamp warned = rs.getTimestamp("warning_sent_at");
        Timestamp reconciled = rs.getTimestamp("actual_reconciled_at");
        return new BudgetWindow(rs.getString("window_type"), rs.getDate("window_start").toLocalDate(),
                rs.getBigDecimal("budget_usd"), rs.getBigDecimal("spent_usd"), rs.getBigDecimal("actual_provider_usd"),
                rs.getBigDecimal("reserved_usd"), warned != null, reconciled == null ? null : reconciled.toInstant());
    }

    private CostReservation reservation(ResultSet rs) throws SQLException {
        return new CostReservation(rs.getObject("request_id", UUID.class), rs.getDate("usage_day").toLocalDate(),
                rs.getDate("usage_month").toLocalDate(), rs.getString("status"), rs.getBigDecimal("reserved_usd"),
                rs.getBigDecimal("input_rate_usd_per_million"), rs.getBigDecimal("cached_input_rate_usd_per_million"),
                rs.getBigDecimal("cache_write_input_rate_usd_per_million"), rs.getBigDecimal("output_rate_usd_per_million"));
    }

    @Transactional
    public void reconcileProviderCosts(java.util.Map<LocalDate, BigDecimal> dailyCosts, LocalDate today) {
        LocalDate month = today.withDayOfMonth(1);
        BigDecimal monthTotal = dailyCosts.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(month) && !entry.getKey().isAfter(today))
                .map(java.util.Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        ensureWindows(today, month);
        for (var entry : dailyCosts.entrySet()) {
            if (entry.getKey().isAfter(today)) continue;
            ensureWindows(entry.getKey(), entry.getKey().withDayOfMonth(1));
            if (entry.getKey().isBefore(today.minusDays(1))) reconcileUnknownReservations(entry.getKey());
            jdbc.update("UPDATE ai_cost_budget_windows SET actual_provider_usd = ?, actual_reconciled_at = clock_timestamp() WHERE window_type = 'DAILY' AND window_start = ?",
                    entry.getValue(), entry.getKey());
        }
        jdbc.update("UPDATE ai_cost_budget_windows SET actual_provider_usd = ?, actual_reconciled_at = clock_timestamp() WHERE window_type = 'MONTHLY' AND window_start = ?",
                monthTotal, month);
        checkWarnings(today, month);
    }

    private void reconcileUnknownReservations(LocalDate day) {
        List<CostReservation> unknown = jdbc.query("SELECT * FROM ai_provider_costs WHERE usage_day = ? AND status = 'UNKNOWN' FOR UPDATE",
                (rs, row) -> reservation(rs), day);
        for (CostReservation reservation : unknown) {
            jdbc.update("""
                    UPDATE ai_cost_budget_windows SET reserved_usd = greatest(0, reserved_usd - ?)
                    WHERE (window_type = 'DAILY' AND window_start = ?) OR (window_type = 'MONTHLY' AND window_start = ?)
                    """, reservation.reservedUsd(), reservation.usageDay(), reservation.usageMonth());
            jdbc.update("UPDATE ai_provider_costs SET status = 'RECONCILED', reserved_usd = 0, settled_at = clock_timestamp() WHERE request_id = ?",
                    reservation.requestId());
        }
    }

    private record BudgetWindow(String type, LocalDate start, BigDecimal budget, BigDecimal spent,
                                BigDecimal actualProvider, BigDecimal reserved, boolean warned,
                                Instant actualReconciledAt) { }
    private record CostReservation(UUID requestId, LocalDate usageDay, LocalDate usageMonth, String status,
                                   BigDecimal reservedUsd, BigDecimal inputRate, BigDecimal cachedInputRate,
                                   BigDecimal cacheWriteInputRate, BigDecimal outputRate) { }
    public record BudgetSummary(String window, LocalDate startsOn, BigDecimal budgetUsd, BigDecimal spentUsd,
                                BigDecimal actualProviderUsd, BigDecimal reservedUsd, BigDecimal totalUsd,
                                BigDecimal percentUsed, boolean warningSent, Instant actualReconciledAt) { }
    public record BudgetStatus(boolean enabled, boolean newRequestsAllowed, boolean databaseEnabled, boolean environmentEnabled,
                               BudgetSummary daily, BudgetSummary monthly, long unknownCharges,
                               long settledRequests, String costBasis, boolean providerReconciliationEnabled,
                               String configuredModel, BigDecimal inputUsdPerMillionTokens,
                               BigDecimal cachedInputUsdPerMillionTokens,
                               BigDecimal cacheWriteInputUsdPerMillionTokens,
                               BigDecimal outputUsdPerMillionTokens) { }
}
