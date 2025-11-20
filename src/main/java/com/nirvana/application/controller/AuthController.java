
package com.nirvana.application.controller;
import com.nirvana.application.model.dto.LoginRequest;
import com.nirvana.application.model.dto.LoginResponse;
import com.nirvana.application.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            String token = tokenProvider.generateToken(auth);
            long expiresAt = tokenProvider.getExpiration(token).getTime();
            return ResponseEntity.ok(new LoginResponse(token, "Bearer", expiresAt, request.getUsername()));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).build();
        }
    }
}
