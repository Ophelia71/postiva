package com.postiva.social.sync;

import com.postiva.social.entity.SocialAccount;
import com.postiva.social.service.SocialAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Removing a channel deletes its history in Postiva: schedules, publish logs, engagement, weekly plans, and posts
 * that no other channel uses. AI usage and billing records stay, because they back quota and cost accounting.
 * Posts already live on the platform are not touched; reconnecting the channel imports them again.
 */
@Service
public class ChannelDeletionService {
    private final SocialAccountService accounts;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ChannelDeletionService(SocialAccountService accounts, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Transactional(readOnly = true)
    public DeletionImpact impact(UUID accountId) {
        SocialAccount account = accounts.requireCurrentUserAccount(accountId);
        return new DeletionImpact(
                countSchedules(account.getId(), List.of("PUBLISHED")),
                countSchedules(account.getId(), List.of("SCHEDULED", "PUBLISHING")),
                countSchedules(account.getId(), List.of("FAILED", "UNKNOWN", "CANCELLED")),
                count("SELECT count(*) FROM content_calendars WHERE social_account_id = ?", account.getId()));
    }

    @Transactional
    public void delete(UUID accountId) {
        SocialAccount account = accounts.requireCurrentUserAccount(accountId);
        UUID id = account.getId();
        if (countSchedules(id, List.of("PUBLISHING")) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Postiva đang đăng bài lên kênh này. Hãy thử xoá lại sau ít phút.");
        }

        // Posts this channel used: its schedules plus drafts written for its weekly plans.
        List<UUID> postIds = jdbc.queryForList("""
                SELECT post_id FROM scheduled_posts WHERE social_account_id = ?
                UNION
                SELECT item.generated_post_id FROM calendar_items item
                JOIN content_calendars plan ON plan.id = item.calendar_id
                WHERE plan.social_account_id = ? AND item.generated_post_id IS NOT NULL
                """, UUID.class, id, id);
        // A post also scheduled on another channel stays: only this channel's part of it goes.
        List<UUID> exclusive = postIds.isEmpty() ? List.of() : named.queryForList("""
                SELECT post.id FROM generated_posts post
                WHERE post.id IN (:ids) AND post.user_id = :userId AND NOT EXISTS (
                    SELECT 1 FROM scheduled_posts other WHERE other.post_id = post.id AND other.social_account_id <> :accountId)
                """, new MapSqlParameterSource().addValue("ids", postIds).addValue("userId", account.getUserId())
                .addValue("accountId", id), UUID.class);
        List<UUID> briefIds = exclusive.isEmpty() ? List.of() : named.queryForList(
                "SELECT DISTINCT brief_id FROM generated_posts WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", exclusive), UUID.class);

        jdbc.update("DELETE FROM content_calendars WHERE social_account_id = ?", id);
        // Publish logs and engagement rows go with their schedules (ON DELETE CASCADE).
        jdbc.update("DELETE FROM scheduled_posts WHERE social_account_id = ?", id);
        if (!exclusive.isEmpty()) {
            named.update("DELETE FROM generated_posts WHERE id IN (:ids)", new MapSqlParameterSource("ids", exclusive));
        }
        if (!briefIds.isEmpty()) {
            // Deleting a brief cascades to its posts, so only briefs no remaining post uses are removed.
            named.update("""
                    DELETE FROM product_briefs brief WHERE brief.id IN (:ids)
                    AND NOT EXISTS (SELECT 1 FROM generated_posts post WHERE post.brief_id = brief.id)
                    """, new MapSqlParameterSource("ids", briefIds));
        }
        accounts.disconnect(id);
    }

    private int countSchedules(UUID accountId, List<String> statuses) {
        Integer value = named.queryForObject(
                "SELECT count(*) FROM scheduled_posts WHERE social_account_id = :id AND status IN (:statuses)",
                new MapSqlParameterSource().addValue("id", accountId).addValue("statuses", statuses), Integer.class);
        return value == null ? 0 : value;
    }

    private int count(String sql, UUID accountId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, accountId);
        return value == null ? 0 : value;
    }
}
