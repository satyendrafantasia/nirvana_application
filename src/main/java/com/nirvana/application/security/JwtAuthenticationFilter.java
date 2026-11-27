package com.nirvana.application.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtProperties jwtProperties;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserDetailsServiceImpl userDetailsService,
                                   JwtProperties jwtProperties) {
        this.jwtTokenService = jwtTokenService;
        this.userDetailsService = userDetailsService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtTokenService.parseClaims(token);
                Long userId = Long.valueOf(claims.getSubject());
                String fingerprintClaim = claims.get("dfp", String.class);
                String fingerprintHeader = request.getHeader(jwtProperties.getDeviceFingerprintHeader());

                boolean deviceMatches = fingerprintClaim == null || fingerprintHeader == null
                        || fingerprintClaim.equals(fingerprintHeader);
                if (deviceMatches && SecurityContextHolder.getContext().getAuthentication() == null) {
                    boolean mfaVerified = Boolean.TRUE.equals(claims.get("mfa", Boolean.class));
                    UserDetails userDetails = userDetailsService.loadSessionUser(userId, claims.getIssuedAt(), mfaVerified, claims.get("sid", String.class));
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute(PaymentMfaVerifier.VERIFIED_SESSION, mfaVerified);
                    if (userDetails instanceof UserPrincipal principal) {
                        request.setAttribute(com.nirvana.application.api.RequestLogFilter.USER_ATTRIBUTE, principal.getId());
                        if (org.slf4j.MDC.get("correlationId") != null) {
                            org.slf4j.MDC.put("userId", principal.getId().toString());
                        }
                    }
                }
            } catch (Exception ex) {
                // Token parsing/validation failed, proceed without authentication so Spring can return 401 where needed
            }
        }

        filterChain.doFilter(request, response);
    }
}
