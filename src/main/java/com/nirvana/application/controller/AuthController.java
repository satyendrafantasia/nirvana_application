package com.nirvana.application.controller;

import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.dto.LoginRequest;
import com.nirvana.application.model.dto.LogoutRequest;
import com.nirvana.application.model.dto.RefreshTokenRequest;
import com.nirvana.application.model.dto.SocialLoginRequest;
import com.nirvana.application.model.dto.UserRegistrationDTO;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.AuthService;
import com.nirvana.application.auth.dto.VerifyOtpRequest;
import com.nirvana.application.auth.service.OtpAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/auth", "/auth"})
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication and session management")
public class AuthController {

    private final AuthService authService;
    private final OtpAuthService otpAuthService;

    @PostMapping("/register")
    @Operation(summary = "Register user", description = "Register a new user and return an authenticated session token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User registered", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class)))
    })
    public AuthResponse register(@Valid @RequestBody UserRegistrationDTO request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate with email/phone credentials and receive JWT token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/login/mfa/verify")
    @Operation(summary = "Verify MFA OTP", description = "Complete OTP-based MFA to finish login")
    public AuthResponse verifyMfa(@Valid @RequestBody VerifyOtpRequest request) {
        return otpAuthService.completeMfa(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate access token", description = "Exchange a valid refresh token for a new access token.")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke refresh tokens for the current device or specific token.")
    public void logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Return profile and roles for the authenticated user.", security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated user", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = com.nirvana.application.api.ApiErrorResponse.class)))
    })
    public AuthResponse me() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("Unauthenticated request");
        }
        return AuthResponse.builder()
                .userId(principal.getId())
                .username(principal.getUsername())
                .roles(principal.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet()))
                .tokenType("Bearer")
                .build();
    }

    @GetMapping("/oauth2/authorize/{provider}")
    @Operation(summary = "Initiate social login", description = "Redirect to configured OAuth2 provider for authentication")
    public void authorizeWithProvider(@PathVariable("provider") String provider,
                                      @RequestParam(value = "redirectUri", required = false) String redirectUri,
                                      HttpServletResponse response) {
        String target = String.format("/oauth2/authorization/%s", provider);
        if (redirectUri != null && !redirectUri.isBlank()) {
            target = target + "?redirect_uri=" + redirectUri;
        }
        response.setHeader("Location", target);
        response.setStatus(HttpServletResponse.SC_FOUND);
    }

    @PostMapping("/oauth2/authorize")
    @Operation(summary = "Initiate social login (POST)", description = "Redirect to configured OAuth2 provider using request body for provider selection")
    public void authorizeWithProvider(@Valid @RequestBody SocialLoginRequest request,
                                      HttpServletResponse response) {
        authorizeWithProvider(request.getProvider(), request.getRedirectUri(), response);
    }

    @GetMapping("/oauth2/callback")
    @Operation(summary = "OAuth2 callback", description = "Exchange OAuth2 user profile for JWT and refresh tokens")
    public AuthResponse oauth2Callback(@RequestParam(value = "provider", required = false) String provider,
                                       @AuthenticationPrincipal OAuth2User principal) {
        if (provider == null || provider.isBlank()) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof OAuth2AuthenticationToken token) {
                provider = token.getAuthorizedClientRegistrationId();
            }
        }
        if (provider == null) {
            throw new IllegalArgumentException("Unable to resolve OAuth2 provider from callback context");
        }
        return authService.socialLogin(provider, principal);
    }
}
