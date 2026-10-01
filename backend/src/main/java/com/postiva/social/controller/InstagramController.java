package com.postiva.social.controller;

import com.postiva.common.ApiResponse;
import com.postiva.common.Platform;
import com.postiva.social.dto.ManualSocialConnectRequest;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.dto.SocialConnectUrlResponse;
import com.postiva.social.service.InstagramOAuthService;
import com.postiva.social.service.MetaSignedRequestService;
import com.postiva.social.service.SocialAccountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/social/instagram")
public class InstagramController {
    private static final Logger log = LoggerFactory.getLogger(InstagramController.class);

    private final InstagramOAuthService oauthService;
    private final SocialAccountService accountService;
    private final MetaSignedRequestService signedRequestService;
    private final String frontendOrigin;

    public InstagramController(InstagramOAuthService oauthService,
                               SocialAccountService accountService,
                               MetaSignedRequestService signedRequestService,
                               @Value("${postiva.frontend-origin}") String frontendOrigin) {
        this.oauthService = oauthService;
        this.accountService = accountService;
        this.signedRequestService = signedRequestService;
        this.frontendOrigin = frontendOrigin;
    }

    @GetMapping("/connect-url")
    public ApiResponse<SocialConnectUrlResponse> connectUrl() {
        return ApiResponse.ok(oauthService.createConnectUrl());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        if (OAuthCallbackRedirects.isBlank(state)) {
            return redirect(OAuthCallbackRedirects.error(frontendOrigin, "instagram", "missing_state"));
        }
        try {
            if (!OAuthCallbackRedirects.isBlank(error)) {
                oauthService.consumeFailedCallbackState(state);
                return redirect(OAuthCallbackRedirects.error(
                        frontendOrigin, "instagram", OAuthCallbackRedirects.providerErrorReason(error)));
            }
            if (OAuthCallbackRedirects.isBlank(code)) {
                oauthService.consumeFailedCallbackState(state);
                return redirect(OAuthCallbackRedirects.error(frontendOrigin, "instagram", "missing_code"));
            }
            oauthService.complete(code, state);
            return redirect(OAuthCallbackRedirects.connected(frontendOrigin, "instagram"));
        } catch (RuntimeException exception) {
            logCallbackFailure(exception);
            return redirect(OAuthCallbackRedirects.error(
                    frontendOrigin, "instagram", OAuthCallbackRedirects.failureReason(exception)));
        }
    }

    @PostMapping("/manual")
    public ApiResponse<SocialAccountResponse> connectManual(
            @Valid @RequestBody ManualSocialConnectRequest request
    ) {
        return ApiResponse.ok(accountService.connectInstagramManual(request));
    }

    @PostMapping("/deauthorize")
    public MetaSignedRequestService.DeauthorizationResponse deauthorize(
            @RequestParam("signed_request") String signedRequest
    ) {
        return signedRequestService.deauthorize(Platform.INSTAGRAM, signedRequest);
    }

    @PostMapping("/delete-data")
    public MetaSignedRequestService.DataDeletionResponse deleteData(
            @RequestParam("signed_request") String signedRequest
    ) {
        MetaSignedRequestService.DeletionReceipt receipt =
                signedRequestService.deleteData(Platform.INSTAGRAM, signedRequest);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/social/instagram/delete-data/status/{code}")
                .buildAndExpand(receipt.confirmationCode())
                .toUriString();
        return new MetaSignedRequestService.DataDeletionResponse(url, receipt.confirmationCode());
    }

    @GetMapping("/delete-data/status/{confirmationCode}")
    public MetaSignedRequestService.DeletionStatus deletionStatus(
            @PathVariable String confirmationCode
    ) {
        return signedRequestService.getDeletionStatus(Platform.INSTAGRAM, confirmationCode);
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    private void logCallbackFailure(RuntimeException exception) {
        HttpStatusCode status = exception instanceof org.springframework.web.server.ResponseStatusException statusException
                ? statusException.getStatusCode()
                : HttpStatus.INTERNAL_SERVER_ERROR;
        log.warn("Instagram OAuth callback failed with status {} ({})",
                status.value(), exception.getClass().getSimpleName());
    }
}
