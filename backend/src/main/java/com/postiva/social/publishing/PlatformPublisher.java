package com.postiva.social.publishing;

import com.postiva.common.Platform;
import com.postiva.file.entity.UploadedFile;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.social.entity.SocialAccount;

import java.util.List;

public interface PlatformPublisher {
    Platform platform();

    String publish(GeneratedPost post,
                   SocialAccount account,
                   String accessToken,
                   List<UploadedFile> mediaFiles);
}
