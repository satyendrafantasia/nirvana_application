package com.nirvana.application.security;

import com.nirvana.application.config.BrowserOrigins;
import com.nirvana.application.config.SecurityConfig;
import com.nirvana.application.model.User;
import com.nirvana.application.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionPolicyTest {
    private final JwtProperties properties = new JwtProperties();
    private final String secret = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(64));

    @Test void sessionContractRequiresIssuerAudienceLifetimeAndPinnedAlgorithm() {
        properties.setSecret(secret);
        var tokens = new JwtTokenService(properties);
        var user = User.builder().id(7L).roles(Set.of(Roles.USER)).build();
        var claims = tokens.parseClaims(tokens.generateToken(user, null, true, "test-family"));
        assertEquals("7", claims.getSubject());
        assertEquals(true, claims.get("mfa"));
        assertFalse(claims.containsKey("email"));
        assertFalse(claims.containsKey("roles"));
        var now = Instant.now();
        for (var item : List.of("audience","issuer","expiry","no-expiry","algorithm")) {
            var fixture = Jwts.builder().setSubject("7").claim("sid","test-family").setIssuedAt(Date.from(now))
                    .setIssuer("issuer".equals(item) ? "other" : properties.getIssuer())
                    .setAudience("audience".equals(item) ? "other" : properties.getAudience());
            if (!"no-expiry".equals(item))
                fixture.setExpiration(Date.from(now.plusSeconds("expiry".equals(item) ? -5 : 60)));
            var algorithm = "algorithm".equals(item) ? SignatureAlgorithm.HS512 : SignatureAlgorithm.HS256;
            var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm.getJcaName());
            var sample = fixture.signWith(key,algorithm).compact();
            assertThrows(io.jsonwebtoken.JwtException.class, () -> tokens.parseClaims(sample), item);
        }
        properties.setSecret("short");
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(properties));
    }

    @Test void privilegeIsRecomputedFromCurrentMembershipOnEveryRequest() {
        UserRepository users = mock(UserRepository.class);
        var user = User.builder().id(7L).roles(Set.of("ROLE_CUSTOMER")).isActive(true).build();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        var sessions = mock(com.nirvana.application.repository.RefreshTokenRepository.class);
        when(sessions.existsByUser_IdAndSessionFamilyAndRevokedAtIsNullAndExpiresAtAfter(eq(7L),eq("active"),any())).thenReturn(true);
        var accounts = new UserDetailsServiceImpl(users,sessions);
        assertTrue(accounts.loadSessionUser(7L,new Date(),false,"active").isEnabled());
        for (String role : List.of(Roles.SPA_OWNER, StaffAccess.DESK_ROLE, Roles.PLATFORM_ADMIN, "ROLE_ADMIN", "ROLE_Spa_MANAGER")) {
            user.setRoles(Set.of(role));
            assertThrows(InsufficientAuthenticationException.class, () -> accounts.loadSessionUser(7L,new Date(),false,"active"));
            assertEquals(role, accounts.loadSessionUser(7L,new Date(),true,"active").getAuthorities().iterator().next().getAuthority());
        }
    }

    @Test void browserSessionCookieAndCorsAreNarrowlyScoped() {
        String cookie = BrowserSession.cookie("unit-fixture",java.time.Duration.ofHours(1));
        for (String attribute : List.of("HttpOnly", "Secure", "SameSite=Strict", "Path=" + BrowserSession.PATH))
            assertTrue(cookie.contains(attribute), attribute);
        assertFalse(cookie.contains("Domain="));
        var origins = new BrowserOrigins();
        var noOrigins = origins.corsConfigurationSource("")
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS","/api/auth/browser-session"));
        assertNull(noOrigins.checkOrigin("http://localhost:5173"));
        var source = origins.corsConfigurationSource("https://nirvana.example");
        var policy = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS","/api/auth/browser-session"));
        assertEquals("https://nirvana.example",policy.checkOrigin("https://nirvana.example"));
        assertNull(policy.checkOrigin("https://another.example"));
        assertNull(policy.checkHeaders(List.of("X-Unknown")));
        assertNull(policy.checkHttpMethod(org.springframework.http.HttpMethod.TRACE));
        assertThrows(IllegalArgumentException.class, () -> origins.corsConfigurationSource("*"));
    }

    @Test void optionalPaymentMfaRequiresServerVerifiedSessionEvidence() {
        var settings = new PaymentMfaVerifier.MfaProperties();
        settings.setEnabled(true);
        var verifier = new PaymentMfaVerifier(settings);
        var request = new MockHttpServletRequest();
        request.addHeader("X-MFA-Token", "test-input");
        var previous = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThrows(AccessDeniedException.class, verifier::verifyPaymentChallenge);
            request.setAttribute(PaymentMfaVerifier.VERIFIED_SESSION, true);
            assertDoesNotThrow(verifier::verifyPaymentChallenge);
        } finally {
            RequestContextHolder.setRequestAttributes(previous);
        }
    }

    @Test void onlySupportedAdaptivePasswordHashesMatch() {
        var encoder = new SecurityConfig(null,null,null,null,null).passwordEncoder();
        String password = "sample-fixture";
        String marked = encoder.encode(password);
        assertTrue(encoder.matches(password, marked));
        assertTrue(encoder.matches(password, marked.substring("{bcrypt}".length())));
        assertFalse(encoder.matches(password, "{noop}" + password));
        assertFalse(encoder.matches(password, password));
    }
}
