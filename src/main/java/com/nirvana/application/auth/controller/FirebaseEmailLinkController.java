package com.nirvana.application.auth.controller;

import com.nirvana.application.auth.dto.RegistrationTokenResponse;
import com.nirvana.application.auth.service.FirebaseEmailLinks;
import com.nirvana.application.auth.service.OtpService;
import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.impl.AuthServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/firebase/email-link")
@RequiredArgsConstructor
public class FirebaseEmailLinkController {
    private final FirebaseEmailLinks links;
    private final OtpService registration;
    private final UserRepository users;
    private final AuthServiceImpl auth;

    public record Proof(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=2048) String code) {}

    @PostMapping("/register")
    public RegistrationTokenResponse register(@Valid @RequestBody Proof proof) {
        return registration.verifiedEmailRegistration(links.redeem(proof.email(), proof.code()));
    }

    @PostMapping("/login")
    @Transactional
    public AuthResponse login(@Valid @RequestBody Proof proof) {
        var email = links.redeem(proof.email(), proof.code());
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No Nirvana account uses this email. Create an account or sign in with your registered mobile number."));
        if (!user.isActive() || !Boolean.TRUE.equals(user.getIsActive()) || user.getDeletedAt() != null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is unavailable.");
        user.setEmailVerified(true);
        return auth.finishSignIn(user);
    }
}
