package com.nirvana.application.controller;

import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.dto.LogoutRequest;
import com.nirvana.application.model.dto.RefreshTokenRequest;
import com.nirvana.application.security.BrowserSession;
import com.nirvana.application.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@RestController
@RequestMapping(value = BrowserSession.PATH, headers = BrowserSession.OPT_IN)
@RequiredArgsConstructor
public class BrowserSessionController {
    private final AuthService auth;

    @PostMapping
    public AuthResponse renew(@CookieValue(value = BrowserSession.COOKIE, required = false) String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in");
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);
        try {
            return auth.refresh(request);
        } catch (jakarta.persistence.EntityNotFoundException | org.springframework.security.core.AuthenticationException
                 | IllegalArgumentException | IllegalStateException invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> logout(@CookieValue(value = BrowserSession.COOKIE, required = false) String token) {
        if (token != null) {
            LogoutRequest request = new LogoutRequest();
            request.setRefreshToken(token);
            auth.logout(request);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, BrowserSession.cookie("", Duration.ZERO)).build();
    }
}
