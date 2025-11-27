package com.nirvana.application.controller;

import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.dto.LoginRequest;
import com.nirvana.application.model.dto.UserRegistrationDTO;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication and session management")
public class AuthController {

    private final AuthService authService;

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
}
