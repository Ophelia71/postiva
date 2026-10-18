package com.postiva.admin.controller;

import com.postiva.admin.dto.AdminPageResponse;
import com.postiva.admin.dto.AdminUserResponse;
import com.postiva.admin.service.AdminUserService;
import com.postiva.auth.security.CustomUserDetailsService;
import com.postiva.auth.security.JwtAuthenticationFilter;
import com.postiva.auth.security.JwtService;
import com.postiva.config.CorsConfig;
import com.postiva.config.SecurityConfig;
import com.postiva.user.entity.User;
import com.postiva.user.service.UserAccountStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
class AdminUserControllerTest {
    @MockBean private com.postiva.auth.security.AuthRequestGuard authRequestGuard;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    @Autowired private MockMvc mvc;
    @MockBean private AdminUserService users;
    @MockBean private UserAccountStatusService statuses;
    @MockBean private JwtService tokens;
    @MockBean private CustomUserDetailsService details;

    @Test
    void anonymousCannotReadOrChangeUsers() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/{id}/lock", USER_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"test\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, statuses);
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserCannotAccessAnyAdminEndpoint() throws Exception {
        for (String path : List.of("", "/" + USER_ID, "/" + USER_ID + "/audit")) {
            mvc.perform(get("/api/admin/users" + path)).andExpect(status().isForbidden());
        }
        for (String action : List.of("lock", "unlock")) {
            mvc.perform(patch("/api/admin/users/{id}/{action}", USER_ID, action)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(users, statuses);
    }

    @Test
    void lockedAdminJwtCannotAuthenticate() throws Exception {
        when(tokens.extractEmail("old-admin-token")).thenReturn("admin@example.test");
        when(details.loadUserByUsername("admin@example.test")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("admin@example.test")
                        .password("hash").roles("ADMIN").accountLocked(true).build());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer old-admin-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "MFA_VERIFIED"})
    void adminCanSearchWithPaginationWithoutReceivingSecrets() throws Exception {
        when(users.list("seller", User.Status.ACTIVE, 1, 10)).thenReturn(
                new AdminPageResponse<>(List.of(AdminUserResponse.from(user())), 1, 10, 11, 2));
        mvc.perform(get("/api/admin/users").param("q", "seller").param("status", "ACTIVE")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items[0].email").value("seller@example.test"))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].accessToken").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "MFA_VERIFIED"})
    void invalidPaginationStatusAndIdsDoNotReachServices() throws Exception {
        mvc.perform(get("/api/admin/users").param("size", "101")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/admin/users").param("page", "-1")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/admin/users").param("status", "OTHER")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(users, statuses);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "MFA_VERIFIED"})
    void reasonIsRequiredForBothStatusTransitions() throws Exception {
        for (String action : List.of("lock", "unlock")) {
            mvc.perform(patch("/api/admin/users/{id}/{action}", USER_ID, action)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
        verifyNoInteractions(statuses);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "MFA_VERIFIED"})
    void lockingDelegatesToTransactionalStatusService() throws Exception {
        User user = user();
        user.lock(UUID.randomUUID(), "Vi phạm");
        when(statuses.lockUser(USER_ID, "Vi phạm")).thenReturn(user);
        mvc.perform(patch("/api/admin/users/{id}/lock", USER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vi phạm\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("LOCKED"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(statuses).lockUser(USER_ID, "Vi phạm");
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "MFA_VERIFIED"})
    void adminCanReadDetailAndPaginatedAudit() throws Exception {
        when(users.detail(USER_ID)).thenReturn(AdminUserResponse.from(user()));
        when(users.audit(USER_ID, 0, 20)).thenReturn(new AdminPageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/admin/users/{id}", USER_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/users/{id}/audit", USER_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void passwordOnlyAdminCannotManageUsers() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        verifyNoInteractions(users, statuses);
    }

    private User user() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setFullName("Seller");
        user.setEmail("seller@example.test");
        user.setPasswordHash("must-never-be-returned");
        return user;
    }
}
