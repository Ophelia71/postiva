package com.postiva.social.controller;

import com.postiva.auth.security.CustomUserDetailsService;
import com.postiva.auth.security.JwtAuthenticationFilter;
import com.postiva.auth.security.JwtService;
import com.postiva.config.CorsConfig;
import com.postiva.config.SecurityConfig;
import com.postiva.social.dto.SocialConnectUrlResponse;
import com.postiva.social.service.FacebookPostSyncService;
import com.postiva.social.service.InstagramOAuthService;
import com.postiva.social.service.MetaOAuthService;
import com.postiva.social.service.MetaSignedRequestService;
import com.postiva.social.service.SocialAccountService;
import com.postiva.social.service.ThreadsOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MetaController.class,
        InstagramController.class,
        ThreadsController.class
})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "postiva.frontend-origin=http://localhost:5173")
class SocialOAuthSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private MetaOAuthService metaOAuthService;

    @MockBean
    private InstagramOAuthService instagramOAuthService;

    @MockBean
    private ThreadsOAuthService threadsOAuthService;

    @MockBean
    private SocialAccountService socialAccountService;

    @MockBean
    private FacebookPostSyncService facebookPostSyncService;

    @MockBean
    private MetaSignedRequestService signedRequestService;

    @Test
    void oauthCallbacksAllowAnonymousRequests() throws Exception {
        when(metaOAuthService.complete("code", "state")).thenReturn(2);

        mockMvc.perform(get("/api/social/meta/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/app/connections?facebook=connected&pages=2"));
        mockMvc.perform(get("/api/social/instagram/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/app/connections?instagram=connected"));
        mockMvc.perform(get("/api/social/threads/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/app/connections?threads=connected"));
    }

    @Test
    void connectAndAccountManagementEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/social/meta/connect-url"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/social/instagram/connect-url"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/social/threads/connect-url"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/social/meta/accounts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/social/meta/accounts/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "oauth-user@example.test")
    void authenticatedUserCanCreateConnectUrls() throws Exception {
        when(metaOAuthService.createConnectUrl()).thenReturn(new SocialConnectUrlResponse("https://example.test", "Meta"));
        when(instagramOAuthService.createConnectUrl()).thenReturn(new SocialConnectUrlResponse("https://example.test", "Instagram"));
        when(threadsOAuthService.createConnectUrl()).thenReturn(new SocialConnectUrlResponse("https://example.test", "Threads"));

        mockMvc.perform(get("/api/social/meta/connect-url"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/social/instagram/connect-url"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/social/threads/connect-url"))
                .andExpect(status().isOk());
    }

    @Test
    void signedProviderCallbacksAllowAnonymousRequests() throws Exception {
        when(signedRequestService.deauthorize(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(new MetaSignedRequestService.DeauthorizationResponse(true, 1));
        when(signedRequestService.deleteData(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(new MetaSignedRequestService.DeletionReceipt("confirmation", 1));

        for (String provider : new String[]{"meta", "instagram", "threads"}) {
            mockMvc.perform(post("/api/social/" + provider + "/deauthorize")
                            .param("signed_request", "signed-request"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/social/" + provider + "/delete-data")
                            .param("signed_request", "signed-request"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void providerCancellationAndCallbackFailuresRedirectWithoutSensitiveDetails() throws Exception {
        mockMvc.perform(get("/api/social/instagram/callback")
                        .param("error", "access_denied")
                        .param("error_description", "sensitive provider response")
                        .param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/app/connections?instagram=error&reason=access_denied"));

        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid state"))
                .when(threadsOAuthService).complete("code", "invalid-state");
        mockMvc.perform(get("/api/social/threads/callback")
                        .param("code", "code")
                        .param("state", "invalid-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/app/connections?threads=error&reason=invalid_state"));

        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "sensitive token response"))
                .when(metaOAuthService).complete("code", "state");
        mockMvc.perform(get("/api/social/meta/callback")
                        .param("code", "code")
                        .param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/app/connections?facebook=error&reason=oauth_failed"));

        doThrow(new MetaOAuthService.NoFacebookPagesException())
                .when(metaOAuthService).complete("sensitive-code", "state");
        mockMvc.perform(get("/api/social/meta/callback")
                        .param("code", "sensitive-code")
                        .param("state", "state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/app/connections?facebook=error&reason=no_pages"));
    }
}
