package com.nirvana.application.controller;

import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.dto.LoginRequest;
import com.nirvana.application.model.dto.UserRegistrationDTO;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody UserRegistrationDTO request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
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
