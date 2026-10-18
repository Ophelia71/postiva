package com.postiva.social.controller;

import com.postiva.common.ApiResponse;
import com.postiva.common.Platform;
import com.postiva.social.dto.ManualSocialConnectRequest;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.service.MetaSignedRequestService;
import com.postiva.social.service.MetaDeletionStatusUrlFactory;
import com.postiva.social.service.SocialAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social/threads")
public class ThreadsController {
    private final SocialAccountService accountService;
    private final MetaSignedRequestService signedRequestService;
    private final MetaDeletionStatusUrlFactory deletionStatusUrls;

    public ThreadsController(SocialAccountService accountService,
                             MetaSignedRequestService signedRequestService,
                             MetaDeletionStatusUrlFactory deletionStatusUrls) {
        this.accountService = accountService;
        this.signedRequestService = signedRequestService;
        this.deletionStatusUrls = deletionStatusUrls;
    }

    @PostMapping("/manual")
    public ApiResponse<SocialAccountResponse> connectManual(
            @Valid @RequestBody ManualSocialConnectRequest request
    ) {
        return ApiResponse.ok(accountService.connectThreadsManual(request));
    }

    @PostMapping("/deauthorize")
    public MetaSignedRequestService.DeauthorizationResponse deauthorize(
            @RequestParam("signed_request") String signedRequest
    ) {
        return signedRequestService.deauthorize(Platform.THREADS, signedRequest);
    }

    @PostMapping("/delete-data")
    public MetaSignedRequestService.DataDeletionResponse deleteData(
            @RequestParam("signed_request") String signedRequest
    ) {
        deletionStatusUrls.requireConfigured();
        MetaSignedRequestService.DeletionReceipt receipt =
                signedRequestService.deleteData(Platform.THREADS, signedRequest);
        return deletionResponse(receipt);
    }

    @GetMapping("/delete-data/status/{confirmationCode}")
    public MetaSignedRequestService.DeletionStatus deletionStatus(
            @PathVariable String confirmationCode
    ) {
        return signedRequestService.getDeletionStatus(Platform.THREADS, confirmationCode);
    }

    private MetaSignedRequestService.DataDeletionResponse deletionResponse(
            MetaSignedRequestService.DeletionReceipt receipt
    ) {
        String url = deletionStatusUrls.statusUrl(Platform.THREADS, receipt.confirmationCode());
        return new MetaSignedRequestService.DataDeletionResponse(url, receipt.confirmationCode());
    }
}
