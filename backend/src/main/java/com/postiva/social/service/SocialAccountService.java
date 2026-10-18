package com.postiva.social.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.dto.FacebookTestPostResponse;
import com.postiva.social.dto.ManualFacebookConnectRequest;
import com.postiva.social.dto.ManualSocialConnectRequest;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.security.TokenCipher;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class SocialAccountService {
    private static final long AUTOMATIC_REFRESH_WINDOW_DAYS = 7;
    private static final long MANUAL_REFRESH_WINDOW_DAYS = 30;
    private static final long MINIMUM_TOKEN_AGE_HOURS = 24;
    private static final List<String> FACEBOOK_SCOPES = List.of(
            "pages_show_list", "pages_read_engagement", "pages_manage_posts"
    );
    private static final List<String> THREADS_SCOPES = List.of(
            "threads_basic", "threads_content_publish"
    );
    private static final List<String> INSTAGRAM_LOGIN_SCOPES = List.of(
            "instagram_business_basic", "instagram_business_content_publish"
    );
    private static final List<String> INSTAGRAM_FACEBOOK_SCOPES = List.of(
            "pages_show_list", "pages_read_engagement", "instagram_basic", "instagram_content_publish"
    );

    private final SocialAccountRepository repository;
    private final CurrentUserService currentUserService;
    private final TokenCipher tokenCipher;
    private final FacebookGraphClient facebookGraphClient;
    private final InstagramGraphClient instagramGraphClient;
    private final ThreadsGraphClient threadsGraphClient;
    private final ScheduledPostRepository scheduledPostRepository;
    private final PublishLogRepository publishLogRepository;
    private final GeneratedPostRepository generatedPostRepository;
    private final ProductBriefRepository productBriefRepository;

    public SocialAccountService(SocialAccountRepository repository,
                                CurrentUserService currentUserService,
                                TokenCipher tokenCipher,
                                FacebookGraphClient facebookGraphClient,
                                InstagramGraphClient instagramGraphClient,
                                ThreadsGraphClient threadsGraphClient,
                                ScheduledPostRepository scheduledPostRepository,
                                PublishLogRepository publishLogRepository,
                                GeneratedPostRepository generatedPostRepository,
                                ProductBriefRepository productBriefRepository) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.tokenCipher = tokenCipher;
        this.facebookGraphClient = facebookGraphClient;
        this.instagramGraphClient = instagramGraphClient;
        this.threadsGraphClient = threadsGraphClient;
        this.scheduledPostRepository = scheduledPostRepository;
        this.publishLogRepository = publishLogRepository;
        this.generatedPostRepository = generatedPostRepository;
        this.productBriefRepository = productBriefRepository;
    }

    @Transactional(readOnly = true)
    public List<SocialAccountResponse> listCurrentUserAccounts() {
        return repository.findByUserIdAndDisconnectedAtIsNullOrderByConnectedAtDesc(
                currentUserService.requireCurrentUserId()).stream().map(this::response).toList();
    }

    @Transactional
    public SocialAccountResponse connectManual(ManualFacebookConnectRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        String pageId = request.pageId().trim();
        String pageName = request.pageName().trim();
        String accessToken = request.pageAccessToken().trim();
        FacebookGraphClient.FacebookPage profile = facebookGraphClient.getPage(pageId, accessToken);
        if (!pageId.equals(trimToNull(profile.id()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Page ID không khớp với Facebook Page mà access token có thể truy cập.");
        }
        return response(saveFacebookPage(
                userId,
                pageId,
                pageName,
                accessToken,
                expiresAt(request.expiresInSeconds())
        ));
    }

    @Transactional
    public SocialAccountResponse connectInstagramManual(ManualSocialConnectRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        String accountId = request.accountId().trim();
        String accountName = request.accountName().trim();
        String accessToken = request.accessToken().trim();
        LocalDateTime expiresAt = expiresAt(request.expiresInSeconds());
        InstagramGraphClient.InstagramProfile profile = instagramGraphClient.getProfile(accountId, accessToken);
        if (!accountId.equals(trimToNull(profile.id()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Instagram ID không khớp với tài khoản mà access token có thể truy cập.");
        }

        return response(saveInstagramAccount(
                userId, accountId, accountName, profile.accountType(), profile.apiMode(), accessToken, expiresAt));
    }

    @Transactional
    public SocialAccountResponse connectThreadsManual(ManualSocialConnectRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        String accountId = request.accountId().trim();
        String accountName = request.accountName().trim();
        String accessToken = request.accessToken().trim();
        LocalDateTime expiresAt = expiresAt(request.expiresInSeconds());
        ThreadsGraphClient.ThreadsProfile profile = threadsGraphClient.getMe(accessToken);
        if (!accountId.equals(trimToNull(profile.id()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Threads ID không khớp với tài khoản của access token.");
        }
        return response(saveThreadsAccount(userId, accountId, accountName, accessToken, expiresAt));
    }

    @Transactional
    public SocialAccount saveFacebookPage(UUID userId, String pageId, String pageName,
                                          String pageAccessToken, LocalDateTime expiresAt) {
        String normalizedPageId = trimToNull(pageId);
        String normalizedAccessToken = trimToNull(pageAccessToken);
        if (normalizedPageId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Meta không trả về Facebook Page ID");
        }
        if (normalizedAccessToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Meta không trả về Page Access Token");
        }
        SocialAccount account = repository.findByUserIdAndProviderAndPageId(
                        userId, Platform.FACEBOOK, normalizedPageId)
                .orElseGet(SocialAccount::new);
        account.setUserId(userId);
        account.setProvider(Platform.FACEBOOK);
        account.setProviderAccountId(normalizedPageId);
        account.setPageId(normalizedPageId);
        account.setPageName(blankToDefault(trimToNull(pageName), "Facebook Page"));
        account.setAccessTokenEncrypted(tokenCipher.encrypt(normalizedAccessToken));
        account.setTokenExpiresAt(expiresAt);
        account.setScopes(FACEBOOK_SCOPES);
        account.setConnectedAt(LocalDateTime.now());
        account.setDisconnectedAt(null);
        return repository.save(account);
    }

    @Transactional
    public SocialAccount saveThreadsAccount(UUID userId, String accountId, String username,
                                            String accessToken, LocalDateTime expiresAt) {
        return saveExternalAccount(
                userId,
                Platform.THREADS,
                accountId,
                displayName(username, "Threads"),
                accessToken,
                expiresAt,
                THREADS_SCOPES,
                Map.of(
                        "username", blankToDefault(username, ""),
                        "providerUserId", accountId
                )
        );
    }

    @Transactional
    public SocialAccount saveInstagramAccount(UUID userId,
                                              String accountId,
                                              String username,
                                              String accountType,
                                              InstagramGraphClient.ApiMode apiMode,
                                              String accessToken,
                                              LocalDateTime expiresAt) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", blankToDefault(username, ""));
        metadata.put("accountType", blankToDefault(accountType, ""));
        metadata.put("apiMode", apiMode.name());
        metadata.put("providerUserId", accountId);
        return saveExternalAccount(
                userId,
                Platform.INSTAGRAM,
                accountId,
                displayName(username, "Instagram"),
                accessToken,
                expiresAt,
                apiMode == InstagramGraphClient.ApiMode.INSTAGRAM_LOGIN
                        ? INSTAGRAM_LOGIN_SCOPES
                        : INSTAGRAM_FACEBOOK_SCOPES,
                metadata
        );
    }

    private SocialAccount saveExternalAccount(UUID userId, Platform provider, String accountId, String accountName,
                                              String accessToken, LocalDateTime expiresAt, List<String> scopes,
                                              Map<String, Object> metadata) {
        if (accountId == null || accountId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, provider.name() + " không trả về account ID");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, provider.name() + " không trả về access token");
        }
        SocialAccount account = repository.findByUserIdAndProviderAndPageId(userId, provider, accountId)
                .orElseGet(SocialAccount::new);
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderAccountId(accountId);
        account.setPageId(accountId);
        account.setPageName(accountName);
        account.setAccessTokenEncrypted(tokenCipher.encrypt(accessToken));
        account.setTokenExpiresAt(expiresAt);
        account.setScopes(scopes);
        account.setAccountMetadata(metadata);
        account.setConnectedAt(LocalDateTime.now());
        account.setDisconnectedAt(null);
        return repository.save(account);
    }

    @Transactional
    public void disconnect(UUID accountId) {
        SocialAccount account = requireCurrentUserAccount(accountId);
        stopAccountSchedules(account);
        account.setAccessTokenEncrypted(null);
        account.setTokenExpiresAt(null);
        account.setDisconnectedAt(LocalDateTime.now());
        repository.save(account);
    }

    @Transactional
    public int removeProviderUserData(Platform provider, String providerUserId, boolean hardDelete) {
        String normalizedUserId = trimToNull(providerUserId);
        if (normalizedUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meta signed_request thiếu user_id");
        }
        List<SocialAccount> accounts = repository.findByProvider(provider).stream()
                .filter(account -> belongsToProviderUser(account, normalizedUserId))
                .toList();
        if (hardDelete) {
            accounts.forEach(this::purgeProviderAccountData);
            repository.deleteAll(accounts);
        } else {
            LocalDateTime disconnectedAt = LocalDateTime.now();
            accounts.forEach(account -> {
                stopAccountSchedules(account);
                account.setAccessTokenEncrypted(null);
                account.setTokenExpiresAt(null);
                account.setDisconnectedAt(disconnectedAt);
            });
            repository.saveAll(accounts);
        }
        return accounts.size();
    }

    private void stopAccountSchedules(SocialAccount account) {
        if (scheduledPostRepository == null || account.getId() == null) return;
        List<ScheduledPost> queued = scheduledPostRepository.findBySocialAccountIdAndStatus(
                account.getId(), ScheduledPostStatus.SCHEDULED);
        queued.forEach(schedule -> {
            schedule.setStatus(ScheduledPostStatus.CANCELLED);
            schedule.setNextAttemptAt(null);
            schedule.setClaimToken(null);
            schedule.setLeaseUntil(null);
            schedule.setLastError("Tài khoản đã ngắt kết nối. Kết nối lại tài khoản và lên lịch lại bài đăng.");
        });
        List<ScheduledPost> inFlight = scheduledPostRepository.findBySocialAccountIdAndStatus(
                account.getId(), ScheduledPostStatus.PUBLISHING);
        inFlight.forEach(schedule -> {
            schedule.setStatus(ScheduledPostStatus.UNKNOWN);
            schedule.setNextAttemptAt(null);
            schedule.setClaimToken(null);
            schedule.setLeaseUntil(null);
            schedule.setLastError("Tài khoản đã ngắt kết nối khi bài đang được gửi. Hãy kiểm tra trên nền tảng trước khi thử lại.");
        });
        scheduledPostRepository.saveAll(queued);
        scheduledPostRepository.saveAll(inFlight);
    }

    private void purgeProviderAccountData(SocialAccount account) {
        if (scheduledPostRepository == null || account.getId() == null) return;
        List<ScheduledPost> schedules = scheduledPostRepository.findBySocialAccountId(account.getId());
        Set<UUID> importedBriefsToDelete = new HashSet<>();
        for (ScheduledPost schedule : schedules) {
            if (schedule.getId() != null && publishLogRepository != null) {
                publishLogRepository.deleteByScheduledPostId(schedule.getId());
            }
            UUID importedBriefId = removeProviderMetadata(account, schedule);
            if (importedBriefId != null) importedBriefsToDelete.add(importedBriefId);
        }
        scheduledPostRepository.deleteAll(schedules);
        if (productBriefRepository != null) {
            importedBriefsToDelete.forEach(productBriefRepository::deleteById);
        }
    }

    private UUID removeProviderMetadata(SocialAccount account, ScheduledPost schedule) {
        if (generatedPostRepository == null || schedule.getPostId() == null) return null;
        GeneratedPost post = generatedPostRepository.findById(schedule.getPostId()).orElse(null);
        if (post == null) return null;

        boolean importedFacebookPost = account.getProvider() == Platform.FACEBOOK
                && productBriefRepository != null
                && productBriefRepository.findById(post.getBriefId())
                .map(brief -> "FACEBOOK_IMPORT".equalsIgnoreCase(brief.getIndustryCode()))
                .orElse(false);
        boolean sharedWithAnotherAccount = scheduledPostRepository.findByPostId(post.getId()).stream()
                .anyMatch(other -> other.getSocialAccountId() != null
                        && !other.getSocialAccountId().equals(account.getId()));
        if (importedFacebookPost && !sharedWithAnotherAccount) {
            return post.getBriefId();
        }

        Map<String, Object> contents = new HashMap<>(post.getPlatformContents() == null
                ? Map.of() : post.getPlatformContents());
        String platformKey = account.getProvider().name();
        Object platformContent = contents.get(platformKey);
        if (!(platformContent instanceof Map<?, ?>)) {
            platformKey = platformKey.toLowerCase();
            platformContent = contents.get(platformKey);
        }
        if (platformContent instanceof Map<?, ?> values) {
            Map<String, Object> cleanContent = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key != null && !"platformPostId".equals(key.toString())
                        && !"permalinkUrl".equals(key.toString())) {
                    cleanContent.put(key.toString(), value);
                }
            });
            contents.put(platformKey, cleanContent);
            post.setPlatformContents(contents);
            generatedPostRepository.save(post);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public SocialAccount requireCurrentUserAccount(UUID accountId) {
        return repository.findByIdAndUserIdAndDisconnectedAtIsNull(
                        accountId, currentUserService.requireCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản Meta"));
    }

    @Transactional(readOnly = true)
    public SocialAccount requireActiveAccount(UUID accountId) {
        return repository.findByIdAndDisconnectedAtIsNull(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài khoản Meta không còn kết nối"));
    }

    @Transactional
    public SocialAccount prepareForPublishing(UUID accountId) {
        SocialAccount account = repository.findByIdAndDisconnectedAtIsNull(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài khoản Meta không còn kết nối"));
        ensureTokenUsable(account);
        if (refreshAccessTokenIfNeeded(account, AUTOMATIC_REFRESH_WINDOW_DAYS)) {
            account = repository.save(account);
        }
        return account;
    }

    @Transactional(readOnly = true)
    public SocialAccount requireCurrentFacebookPage(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Facebook Page ID là bắt buộc");
        }
        return repository.findByUserIdAndProviderAndPageId(
                        currentUserService.requireCurrentUserId(), Platform.FACEBOOK, pageId.trim())
                .filter(account -> account.getDisconnectedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facebook Page chưa được kết nối"));
    }

    @Transactional(readOnly = true)
    public SocialAccountResponse checkCurrentAccount(UUID accountId) {
        SocialAccount account = requireCurrentUserAccount(accountId);
        ensureTokenUsable(account);
        fetchRemoteProfile(account);
        return response(account);
    }

    @Transactional
    public SocialAccountResponse refreshCurrentAccount(UUID accountId) {
        SocialAccount account = requireCurrentUserAccount(accountId);
        ensureTokenUsable(account);
        refreshAccessTokenIfNeeded(account, MANUAL_REFRESH_WINDOW_DAYS);
        switch (account.getProvider()) {
            case FACEBOOK -> refreshFacebookAccount(account);
            case INSTAGRAM -> refreshInstagramAccount(account);
            case THREADS -> refreshThreadsAccount(account);
        }
        return response(repository.save(account));
    }

    @Transactional(readOnly = true)
    public FacebookTestPostResponse publishTestPost(String pageId, String message) {
        SocialAccount account = requireCurrentFacebookPage(pageId);
        ensureTokenUsable(account);
        String externalPostId = facebookGraphClient.publishUnpublishedText(
                account.getPageId(), decryptToken(account), message.trim());
        return new FacebookTestPostResponse(account.getPageId(), externalPostId);
    }

    public String decryptToken(SocialAccount account) {
        return tokenCipher.decrypt(account.getAccessTokenEncrypted());
    }

    void ensureTokenUsable(SocialAccount account) {
        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, providerLabel(account) + " token đã hết hạn, hãy kết nối lại tài khoản");
        }
        if (account.getAccessTokenEncrypted() == null || account.getAccessTokenEncrypted().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, providerLabel(account) + " chưa có access token");
        }
    }

    private void fetchRemoteProfile(SocialAccount account) {
        String accessToken = decryptToken(account);
        switch (account.getProvider()) {
            case FACEBOOK -> facebookGraphClient.getPage(account.getPageId(), accessToken);
            case INSTAGRAM -> instagramGraphClient.getProfile(account.getPageId(), accessToken);
            case THREADS -> threadsGraphClient.getMe(accessToken);
        }
    }

    private void refreshFacebookAccount(SocialAccount account) {
        FacebookGraphClient.FacebookPage profile = facebookGraphClient.getPage(
                account.getPageId(), decryptToken(account));
        String profileId = trimToNull(profile.id());
        String profileName = trimToNull(profile.name());
        if (profileId != null) {
            account.setProviderAccountId(profileId);
            account.setPageId(profileId);
        }
        if (profileName != null) {
            account.setPageName(profileName);
        }
    }

    private void refreshInstagramAccount(SocialAccount account) {
        InstagramGraphClient.InstagramProfile profile = instagramGraphClient.getProfile(
                account.getPageId(), decryptToken(account));
        String profileId = trimToNull(profile.id());
        String username = trimToNull(profile.username());
        if (profileId != null) {
            account.setProviderAccountId(profileId);
            account.setPageId(profileId);
        }
        if (username != null) {
            account.setPageName(displayName(username, "Instagram"));
        }
        Map<String, Object> metadata = accountMetadata(account);
        metadata.put("username", blankToDefault(username, ""));
        metadata.put("accountType", blankToDefault(profile.accountType(), ""));
        metadata.put("apiMode", profile.apiMode().name());
        account.setAccountMetadata(metadata);
    }

    private void refreshThreadsAccount(SocialAccount account) {
        ThreadsGraphClient.ThreadsProfile profile = threadsGraphClient.getMe(decryptToken(account));
        String profileId = trimToNull(profile.id());
        String username = trimToNull(profile.username());
        if (profileId != null) {
            account.setProviderAccountId(profileId);
            account.setPageId(profileId);
        }
        if (username != null) {
            account.setPageName(displayName(username, "Threads"));
        }
        Map<String, Object> metadata = accountMetadata(account);
        metadata.put("username", blankToDefault(username, ""));
        account.setAccountMetadata(metadata);
    }

    private Map<String, Object> accountMetadata(SocialAccount account) {
        return account.getAccountMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(account.getAccountMetadata());
    }

    private boolean refreshAccessTokenIfNeeded(SocialAccount account, long windowDays) {
        if (!shouldRefreshAccessToken(account, windowDays)) {
            return false;
        }

        String currentToken = decryptToken(account);
        if (account.getProvider() == Platform.INSTAGRAM && isInstagramLogin(account)) {
            InstagramGraphClient.AccessToken refreshed = instagramGraphClient.refreshLongLived(currentToken);
            applyRefreshedToken(account, refreshed.accessToken(), refreshed.expiresInSeconds());
            return true;
        }
        if (account.getProvider() == Platform.THREADS) {
            ThreadsGraphClient.AccessToken refreshed = threadsGraphClient.refreshLongLived(currentToken);
            applyRefreshedToken(account, refreshed.accessToken(), refreshed.expiresInSeconds());
            return true;
        }
        return false;
    }

    private boolean shouldRefreshAccessToken(SocialAccount account, long windowDays) {
        LocalDateTime expiresAt = account.getTokenExpiresAt();
        if (expiresAt == null || !expiresAt.isBefore(LocalDateTime.now().plusDays(windowDays))) {
            return false;
        }
        LocalDateTime connectedAt = account.getConnectedAt();
        return connectedAt == null || connectedAt.isBefore(LocalDateTime.now().minusHours(MINIMUM_TOKEN_AGE_HOURS));
    }

    private boolean isInstagramLogin(SocialAccount account) {
        Map<String, Object> metadata = account.getAccountMetadata();
        return metadata != null
                && InstagramGraphClient.ApiMode.INSTAGRAM_LOGIN.name().equalsIgnoreCase(
                        String.valueOf(metadata.getOrDefault("apiMode", "")));
    }

    private boolean belongsToProviderUser(SocialAccount account, String providerUserId) {
        if (providerUserId.equals(account.getPageId()) || providerUserId.equals(account.getProviderAccountId())) {
            return true;
        }
        Map<String, Object> metadata = account.getAccountMetadata();
        if (metadata == null) {
            return false;
        }
        return providerUserId.equals(String.valueOf(metadata.get("providerUserId")))
                || providerUserId.equals(String.valueOf(metadata.get("oauthUserId")));
    }

    private void applyRefreshedToken(SocialAccount account, String accessToken, long expiresInSeconds) {
        account.setAccessTokenEncrypted(tokenCipher.encrypt(accessToken));
        if (expiresInSeconds > 0) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));
        }
        Map<String, Object> metadata = accountMetadata(account);
        metadata.put("tokenRefreshedAt", LocalDateTime.now().toString());
        account.setAccountMetadata(metadata);
    }

    private SocialAccountResponse response(SocialAccount account) {
        return new SocialAccountResponse(
                account.getId(), account.getProvider(), account.getPageId(), account.getPageName(),
                account.getTokenExpiresAt(), account.getConnectedAt()
        );
    }

    private String displayName(String username, String fallback) {
        String value = blankToDefault(username, fallback);
        return value.startsWith("@") ? value : "@" + value;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String providerLabel(SocialAccount account) {
        if (account == null || account.getProvider() == null) {
            return "Meta";
        }
        return switch (account.getProvider()) {
            case FACEBOOK -> "Facebook Page";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
    }

    private LocalDateTime expiresAt(Long expiresInSeconds) {
        return expiresInSeconds == null || expiresInSeconds <= 0
                ? null : LocalDateTime.now().plusSeconds(expiresInSeconds);
    }
}
