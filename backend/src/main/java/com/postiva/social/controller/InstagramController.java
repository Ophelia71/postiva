package com.postiva.social.controller;

import com.postiva.common.ApiResponse;
import com.postiva.common.Platform;
import com.postiva.social.dto.ManualSocialConnectRequest;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.service.MetaSignedRequestService;
import com.postiva.social.service.MetaDeletionStatusUrlFactory;
import com.postiva.social.service.SocialAccountService;
import com.postiva.social.sync.ChannelSyncService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social/instagram")
public class InstagramController {
    private final SocialAccountService accountService;
    private final MetaSignedRequestService signedRequestService;
    private final MetaDeletionStatusUrlFactory deletionStatusUrls;
    private final ChannelSyncService channelSync;

    public InstagramController(SocialAccountService accountService,
                               MetaSignedRequestService signedRequestService,
                               MetaDeletionStatusUrlFactory deletionStatusUrls,
                               ChannelSyncService channelSync) {
        this.accountService = accountService;
        this.signedRequestService = signedRequestService;
        this.deletionStatusUrls = deletionStatusUrls;
        this.channelSync = channelSync;
    }

    @PostMapping("/manual")
    public ApiResponse<SocialAccountResponse> connectManual(
            @Valid @RequestBody ManualSocialConnectRequest request
    ) {
        SocialAccountResponse connected = accountService.connectInstagramManual(request);
        // Bring existing posts and their engagement in without making the shop wait.
        if (connected != null) channelSync.syncInBackground(connected.id());
        return ApiResponse.ok(connected);
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
        deletionStatusUrls.requireConfigured();
        MetaSignedRequestService.DeletionReceipt receipt =
                signedRequestService.deleteData(Platform.INSTAGRAM, signedRequest);
        String url = deletionStatusUrls.statusUrl(Platform.INSTAGRAM, receipt.confirmationCode());
        return new MetaSignedRequestService.DataDeletionResponse(url, receipt.confirmationCode());
    }

    @GetMapping("/delete-data/status/{confirmationCode}")
    public MetaSignedRequestService.DeletionStatus deletionStatus(
            @PathVariable String confirmationCode
    ) {
        return signedRequestService.getDeletionStatus(Platform.INSTAGRAM, confirmationCode);
    }

}
