package com.postiva.social.controller;

import com.postiva.common.ApiResponse;
import com.postiva.common.Platform;
import com.postiva.social.dto.FacebookSyncResponse;
import com.postiva.social.dto.FacebookTestPostRequest;
import com.postiva.social.dto.FacebookTestPostResponse;
import com.postiva.social.dto.ManualFacebookConnectRequest;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.dto.SocialConnectUrlResponse;
import com.postiva.social.service.MetaOAuthService;
import com.postiva.social.service.MetaSignedRequestService;
import com.postiva.social.service.SocialAccountService;
import com.postiva.social.service.FacebookPostSyncService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/social/meta")
public class MetaController {
    private final MetaOAuthService oauthService;
    private final SocialAccountService accountService;
    private final FacebookPostSyncService facebookPostSyncService;
    private final MetaSignedRequestService signedRequestService;
    private final String frontendOrigin;

    public MetaController(
            MetaOAuthService oauthService,
            SocialAccountService accountService,
            FacebookPostSyncService facebookPostSyncService,
            MetaSignedRequestService signedRequestService,
            @Value("${postiva.frontend-origin}") String frontendOrigin
    ) {
        this.oauthService = oauthService;
        this.accountService = accountService;
        this.facebookPostSyncService = facebookPostSyncService;
        this.signedRequestService = signedRequestService;
        this.frontendOrigin = frontendOrigin;
    }

    @GetMapping("/connect-url")
    public ApiResponse<SocialConnectUrlResponse> connectUrl() {
        return ApiResponse.ok(oauthService.createConnectUrl());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        int pageCount = oauthService.complete(code, state);
        URI location = connectionsUri("facebook=connected&pages=" + pageCount);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    @GetMapping("/accounts")
    public ApiResponse<List<SocialAccountResponse>> accounts() {
        return ApiResponse.ok(accountService.listCurrentUserAccounts());
    }

    @PostMapping("/facebook/manual")
    public ApiResponse<SocialAccountResponse> connectManual(
            @Valid @RequestBody ManualFacebookConnectRequest request
    ) {
        return ApiResponse.ok(accountService.connectManual(request));
    }

    @PostMapping("/facebook/test-post")
    public ApiResponse<FacebookTestPostResponse> testPost(
            @Valid @RequestBody FacebookTestPostRequest request
    ) {
        return ApiResponse.ok(accountService.publishTestPost(request.pageId(), request.message()));
    }

    @PostMapping("/facebook/sync-posts")
    public ApiResponse<FacebookSyncResponse> syncPosts(@RequestParam String pageId) {
        return ApiResponse.ok(facebookPostSyncService.sync(pageId));
    }

    @PostMapping("/accounts/{accountId}/check")
    public ApiResponse<SocialAccountResponse> checkAccount(@PathVariable UUID accountId) {
        return ApiResponse.ok(accountService.checkCurrentAccount(accountId));
    }

    @PostMapping("/accounts/{accountId}/refresh")
    public ApiResponse<SocialAccountResponse> refreshAccount(@PathVariable UUID accountId) {
        return ApiResponse.ok(accountService.refreshCurrentAccount(accountId));
    }

    @DeleteMapping("/accounts/{accountId}")
    public ApiResponse<Void> disconnect(@PathVariable UUID accountId) {
        accountService.disconnect(accountId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/deauthorize")
    public MetaSignedRequestService.DeauthorizationResponse deauthorize(
            @RequestParam("signed_request") String signedRequest
    ) {
        return signedRequestService.deauthorize(Platform.FACEBOOK, signedRequest);
    }

    @PostMapping("/delete-data")
    public MetaSignedRequestService.DataDeletionResponse deleteData(
            @RequestParam("signed_request") String signedRequest
    ) {
        MetaSignedRequestService.DeletionReceipt receipt =
                signedRequestService.deleteData(Platform.FACEBOOK, signedRequest);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/social/meta/delete-data/status/{code}")
                .buildAndExpand(receipt.confirmationCode())
                .toUriString();
        return new MetaSignedRequestService.DataDeletionResponse(url, receipt.confirmationCode());
    }

    @GetMapping("/delete-data/status/{confirmationCode}")
    public MetaSignedRequestService.DeletionStatus deletionStatus(@PathVariable String confirmationCode) {
        return signedRequestService.getDeletionStatus(Platform.FACEBOOK, confirmationCode);
    }

    private URI connectionsUri(String query) {
        return URI.create(frontendOrigin.replaceAll("/+$", "") + "/app/connections?" + query);
    }
}
