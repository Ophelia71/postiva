package com.postiva.social.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.dto.FacebookTestPostResponse;
import com.postiva.social.dto.ManualFacebookConnectRequest;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.security.TokenCipher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SocialAccountService {
    private static final List<String> FACEBOOK_SCOPES = List.of(
            "pages_show_list", "pages_read_engagement", "pages_manage_posts"
    );

    private final SocialAccountRepository repository;
    private final CurrentUserService currentUserService;
    private final TokenCipher tokenCipher;
    private final FacebookGraphClient facebookGraphClient;

    public SocialAccountService(SocialAccountRepository repository,
                                CurrentUserService currentUserService,
                                TokenCipher tokenCipher,
                                FacebookGraphClient facebookGraphClient) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.tokenCipher = tokenCipher;
        this.facebookGraphClient = facebookGraphClient;
    }

    @Transactional(readOnly = true)
    public List<SocialAccountResponse> listCurrentUserAccounts() {
        return repository.findByUserIdAndDisconnectedAtIsNullOrderByConnectedAtDesc(
                currentUserService.requireCurrentUserId()).stream().map(this::response).toList();
    }

    @Transactional
    public SocialAccountResponse connectManual(ManualFacebookConnectRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        LocalDateTime expiresAt = request.expiresInSeconds() == null || request.expiresInSeconds() <= 0
                ? null : LocalDateTime.now().plusSeconds(request.expiresInSeconds());
        return response(saveFacebookPage(userId, request.pageId(), request.pageName(),
                request.pageAccessToken(), expiresAt));
    }

    @Transactional
    public SocialAccount saveFacebookPage(UUID userId, String pageId, String pageName,
                                          String pageAccessToken, LocalDateTime expiresAt) {
        SocialAccount account = repository.findByUserIdAndProviderAndPageId(userId, Platform.FACEBOOK, pageId)
                .orElseGet(SocialAccount::new);
        account.setUserId(userId);
        account.setProvider(Platform.FACEBOOK);
        account.setProviderAccountId(pageId);
        account.setPageId(pageId);
        account.setPageName(pageName);
        account.setAccessTokenEncrypted(tokenCipher.encrypt(pageAccessToken));
        account.setTokenExpiresAt(expiresAt);
        account.setScopes(FACEBOOK_SCOPES);
        account.setConnectedAt(LocalDateTime.now());
        account.setDisconnectedAt(null);
        return repository.save(account);
    }

    @Transactional
    public void disconnect(UUID accountId) {
        SocialAccount account = requireCurrentUserAccount(accountId);
        account.setDisconnectedAt(LocalDateTime.now());
        repository.save(account);
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Meta token đã hết hạn, hãy kết nối lại Page");
        }
        if (account.getAccessTokenEncrypted() == null || account.getAccessTokenEncrypted().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Facebook Page chưa có Page Access Token");
        }
    }

    private SocialAccountResponse response(SocialAccount account) {
        return new SocialAccountResponse(
                account.getId(), account.getProvider(), account.getPageId(), account.getPageName(),
                account.getTokenExpiresAt(), account.getConnectedAt()
        );
    }
}
