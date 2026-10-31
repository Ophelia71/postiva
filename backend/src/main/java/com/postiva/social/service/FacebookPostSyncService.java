package com.postiva.social.service;

import com.postiva.social.dto.FacebookSyncResponse;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.sync.ChannelSyncService;
import com.postiva.social.sync.SyncResult;
import org.springframework.stereotype.Service;

/**
 * The original Facebook-only sync endpoint. It now runs the shared channel sync, which also stores engagement and
 * only removes posts inside the window it just read (the old version could drop every post on an empty reply).
 */
@Service
public class FacebookPostSyncService {
    private final SocialAccountService accountService;
    private final ChannelSyncService channelSync;

    public FacebookPostSyncService(SocialAccountService accountService, ChannelSyncService channelSync) {
        this.accountService = accountService;
        this.channelSync = channelSync;
    }

    public FacebookSyncResponse sync(String pageId) {
        SocialAccount account = accountService.requireCurrentFacebookPage(pageId);
        SyncResult result = channelSync.sync(account.getId());
        return new FacebookSyncResponse(account.getPageId(), result.imported() + result.updated(), result.removed());
    }
}
