package com.postiva.social.controller;

import com.postiva.auth.security.CustomUserDetailsService;
import com.postiva.auth.security.JwtAuthenticationFilter;
import com.postiva.auth.security.JwtService;
import com.postiva.config.CorsConfig;
import com.postiva.config.SecurityConfig;
import com.postiva.social.service.FacebookPostSyncService;
import com.postiva.social.service.MetaSignedRequestService;
import com.postiva.social.service.SocialAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MetaController.class,
        InstagramController.class,
        ThreadsController.class
})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "postiva.frontend-origin=http://localhost:5173")
class SocialConnectionSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private SocialAccountService socialAccountService;

    @MockBean
    private FacebookPostSyncService facebookPostSyncService;

    @MockBean
    private MetaSignedRequestService signedRequestService;

    @Test
    void manualConnectionAndAccountManagementRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/social/meta/facebook/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facebookConnectionBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/social/instagram/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(socialConnectionBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/social/threads/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(socialConnectionBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/social/meta/accounts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/social/meta/accounts/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "manual-user@example.test")
    void authenticatedUserCanConnectWithIdNameAndAccessToken() throws Exception {
        mockMvc.perform(post("/api/social/meta/facebook/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facebookConnectionBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/social/instagram/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(socialConnectionBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/social/threads/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(socialConnectionBody()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manual-user@example.test")
    void manualConnectionRequiresIdNameAndAccessToken() throws Exception {
        mockMvc.perform(post("/api/social/meta/facebook/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageId": "page-123",
                                  "pageAccessToken": "page-token"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/social/instagram/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Postiva",
                                  "accessToken": "account-token"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "manual-user@example.test")
    void removedOAuthEndpointsReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/social/meta/connect-url"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/social/instagram/callback"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/social/threads/connect-url"))
                .andExpect(status().isNotFound());
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

    private String facebookConnectionBody() {
        return """
                {
                  "pageId": "page-123",
                  "pageName": "Postiva",
                  "pageAccessToken": "page-token"
                }
                """;
    }

    private String socialConnectionBody() {
        return """
                {
                  "accountId": "account-123",
                  "accountName": "Postiva",
                  "accessToken": "account-token"
                }
                """;
    }
}
