package com.postiva.auth.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.ArrayList;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization =
                request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7).trim();

        try {
            String email = jwtService.extractEmail(token);

            if (email != null
                    && SecurityContextHolder.getContext()
                    .getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()
                        && userDetails.isAccountNonExpired() && userDetails.isCredentialsNonExpired()
                        && jwtService.isTokenValid(token, userDetails)) {
                    var authorities = new ArrayList<GrantedAuthority>(userDetails.getAuthorities());
                    if (userDetails instanceof PostivaUserDetails account && account.isMfaEnabled()
                            && jwtService.isMfaVerified(token)) {
                        authorities.add(new SimpleGrantedAuthority("MFA_VERIFIED"));
                    }
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    authorities
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }
        } catch (JwtException
                 | AuthenticationException
                 | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
