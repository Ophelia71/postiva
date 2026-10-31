package com.postiva.social.service;

import com.postiva.common.Platform;
import com.postiva.mail.EmailService;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The shop is emailed at 7, 3 and 1 days before an access token expires, each step once. */
class TokenExpiryReminderServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final SocialAccountRepository accounts = mock(SocialAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 1, 9, 0);
    private TokenExpiryReminderService service;

    @BeforeEach
    void setUp() {
        service = new TokenExpiryReminderService(accounts, users, email, "https://postiva.store/");
        User owner = mock(User.class);
        when(owner.getEmail()).thenReturn("shop@example.com");
        when(owner.getFullName()).thenReturn("Ophelia");
        when(users.findById(userId)).thenReturn(Optional.of(owner));
    }

    private SocialAccount account(Platform provider, LocalDateTime expiresAt) {
        SocialAccount account = new SocialAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        account.setPageName("Geminity");
        account.setTokenExpiresAt(expiresAt);
        account.setAccountMetadata(new HashMap<>());
        when(accounts.findByDisconnectedAtIsNullAndTokenExpiresAtBetween(any(), any())).thenReturn(List.of(account));
        return account;
    }

    @Test
    void emailsOnceWhenSevenDaysAreLeftAndNotAgainOnTheNextRun() {
        account(Platform.FACEBOOK, now.plusDays(6).plusHours(10));

        assertEquals(1, service.remindDue(now));
        assertEquals(0, service.remindDue(now.plusHours(6)));

        verify(email, times(1)).sendTokenExpiring(eq("shop@example.com"), eq("Ophelia"), eq("Facebook Geminity"), eq(7L),
                eq("https://postiva.store/app/connections"));
    }

    @Test
    void sendsAgainWhenTheTokenGetsCloserToExpiry() {
        SocialAccount account = account(Platform.FACEBOOK, now.plusDays(6).plusHours(10));
        service.remindDue(now);

        service.remindDue(now.plusDays(4));
        service.remindDue(now.plusDays(5).plusHours(12));
        service.remindDue(now.plusDays(5).plusHours(18));

        verify(email, times(3)).sendTokenExpiring(any(), any(), any(), anyLong(), any());
        assertEquals(1, account.getAccountMetadata().get(TokenExpiryReminderService.REMINDED_LEVEL));
    }

    @Test
    void reconnectingWithANewExpiryStartsTheStepsOver() {
        SocialAccount account = account(Platform.FACEBOOK, now.plusDays(2));
        service.remindDue(now);

        account.setTokenExpiresAt(now.plusDays(60));
        account.setTokenExpiresAt(now.plusDays(2).plusHours(1));
        service.remindDue(now);

        verify(email, times(2)).sendTokenExpiring(any(), any(), any(), anyLong(), any());
    }

    @Test
    void aFailedEmailIsTriedAgainNextTime() {
        account(Platform.FACEBOOK, now.plusDays(2));
        doThrow(new IllegalStateException("smtp down")).doNothing()
                .when(email).sendTokenExpiring(any(), any(), any(), anyLong(), any());

        assertEquals(0, service.remindDue(now));
        assertEquals(1, service.remindDue(now.plusHours(6)));
    }

    @Test
    void threadsAndInstagramLoginRenewThemselvesSoTheyAreLeftOut() {
        account(Platform.THREADS, now.plusDays(2));
        assertEquals(0, service.remindDue(now));

        SocialAccount instagram = account(Platform.INSTAGRAM, now.plusDays(2));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("apiMode", "INSTAGRAM_LOGIN");
        instagram.setAccountMetadata(metadata);
        assertEquals(0, service.remindDue(now));

        verify(email, never()).sendTokenExpiring(any(), any(), any(), anyLong(), any());
    }

    @Test
    void daysAreRoundedUpAndNeverBelowOne() {
        assertEquals(1, TokenExpiryReminderService.levelFor(now, now.plusHours(3)));
        assertEquals(3, TokenExpiryReminderService.levelFor(now, now.plusDays(2).plusHours(5)));
        assertEquals(7, TokenExpiryReminderService.levelFor(now, now.plusDays(6)));
        assertEquals(1, TokenExpiryReminderService.daysLeft(now, now.plusMinutes(10)));
    }
}
