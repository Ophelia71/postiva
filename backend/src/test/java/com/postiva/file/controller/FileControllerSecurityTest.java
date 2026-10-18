package com.postiva.file.controller;

import com.postiva.auth.security.AuthRequestGuard;
import com.postiva.auth.security.CustomUserDetailsService;
import com.postiva.auth.security.JwtAuthenticationFilter;
import com.postiva.auth.security.JwtService;
import com.postiva.config.CorsConfig;
import com.postiva.config.SecurityConfig;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "postiva.frontend-origin=http://localhost:5173")
class FileControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private AuthRequestGuard authRequestGuard;
    @MockBean private JwtService jwtService;
    @MockBean private CustomUserDetailsService userDetailsService;
    @MockBean private FileService fileService;

    @TempDir Path tempDir;
    private final UUID fileId = UUID.randomUUID();
    private UploadedFile file;

    @BeforeEach
    void setUp() throws Exception {
        Path resourcePath = tempDir.resolve("media.png");
        Files.write(resourcePath, new byte[]{1, 2, 3});
        file = new UploadedFile();
        file.setFileName("media.png");
        file.setMimeType("image/png");
        file.setStorageProvider("LOCAL");
        when(fileService.resource(file)).thenReturn(new FileSystemResource(resourcePath));
        when(fileService.requireFile(fileId)).thenReturn(file);
        when(fileService.requirePublicFile(eq(fileId), anyLong(), anyString())).thenReturn(file);
    }

    @Test
    void privateDownloadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/files/{fileId}", fileId))
                .andExpect(status().isUnauthorized());

        verify(fileService, never()).requireFile(fileId);
    }

    @Test
    @WithMockUser(roles = "USER")
    void signedInUserCanReachOwnerCheckedPrivateDownload() throws Exception {
        mockMvc.perform(get("/api/files/{fileId}", fileId))
                .andExpect(status().isOk());

        verify(fileService).requireFile(fileId);
    }

    @Test
    void validSignedPublicRouteRemainsAvailableWithoutLoginForMeta() throws Exception {
        mockMvc.perform(get("/api/files/public/{fileId}", fileId)
                        .param("expires", Long.toString(System.currentTimeMillis() / 1000 + 60))
                        .param("signature", "signed-fixture"))
                .andExpect(status().isOk());

        verify(fileService).requirePublicFile(eq(fileId), anyLong(), eq("signed-fixture"));
    }
}
