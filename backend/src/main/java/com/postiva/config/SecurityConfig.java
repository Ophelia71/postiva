package com.postiva.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.auth.security.JwtAuthenticationFilter;
import com.postiva.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeApiError(response, objectMapper, HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeApiError(response, objectMapper, HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/templates/**", "/api/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/social/meta/callback",
                                "/api/social/threads/callback",
                                "/api/social/instagram/callback").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/social/meta/deauthorize",
                                "/api/social/meta/delete-data",
                                "/api/social/instagram/deauthorize",
                                "/api/social/instagram/delete-data",
                                "/api/social/threads/deauthorize",
                                "/api/social/threads/delete-data").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/social/meta/delete-data/status/*",
                                "/api/social/instagram/delete-data/status/*",
                                "/api/social/threads/delete-data/status/*").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeApiError(HttpServletResponse response,
                               ObjectMapper objectMapper,
                               HttpStatus status,
                               String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(message));
    }
}
