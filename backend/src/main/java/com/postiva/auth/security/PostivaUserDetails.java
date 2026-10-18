package com.postiva.auth.security;

import com.postiva.user.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public final class PostivaUserDetails extends org.springframework.security.core.userdetails.User {
    private final int authVersion;
    private final boolean mfaEnabled;

    public PostivaUserDetails(User user) {
        super(user.getEmail(), user.getPasswordHash(), true, true, true, user.isActive(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.authVersion = user.getAuthVersion();
        this.mfaEnabled = user.isMfaEnabled();
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public boolean isMfaEnabled() { return mfaEnabled; }
}
