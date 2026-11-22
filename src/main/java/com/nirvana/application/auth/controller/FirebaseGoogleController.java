package com.nirvana.application.auth.controller;

import com.nirvana.application.auth.service.OtpAuthService;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.otp.FirebasePhoneTokens;
import com.nirvana.application.otp.FirebasePhoneTokens.GoogleIdentity;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.impl.AuthServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class FirebaseGoogleController {
    private final FirebasePhoneTokens tokens;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final AuthServiceImpl auth;
    private final OtpAuthService otp;

    public record SignIn(@NotBlank @Size(max=8192) String idToken, boolean createCustomer) {}
    public record Connect(@NotBlank @Size(max=8192) String idToken, @NotBlank @Size(max=128) String loginToken) {}
    public record Status(boolean customer, String email) {}

    @PostMapping("/api/auth/firebase/google")
    @Transactional
    public AuthResponse signIn(@Valid @RequestBody SignIn request) {
        var identity = tokens.verifiedGoogle(request.idToken());
        var linked = jdbc.queryForList("SELECT user_id FROM customer_google_identity WHERE project_id=? AND firebase_uid=?",
                Long.class, identity.project(), identity.uid());
        if (!linked.isEmpty()) return auth.finishSignIn(customer(linked.get(0)));
        if (!request.createCustomer() || users.existsByEmail(identity.email()) || users.existsByUsername(identity.email()))
            throw conflict("Sign in with your existing method and choose Connect Google in My Account. New customers can choose Create an account.");
        User user = auth.createGoogleCustomer(identity.email());
        saveLink(user.getId(), identity);
        return auth.finishSignIn(user);
    }

    @GetMapping("/api/me/account/google")
    public Status status() {
        long id = SecurityUtils.getCurrentUserId();
        var user = users.findById(id).orElseThrow();
        var email = jdbc.queryForList("SELECT google_email FROM customer_google_identity WHERE user_id=?", String.class, id);
        return new Status(user.getRole().getRoleType() == RoleType.CUSTOMER, email.stream().findFirst().orElse(null));
    }

    @PostMapping("/api/me/account/google")
    @Transactional
    public Status connect(@Valid @RequestBody Connect request) {
        long id = SecurityUtils.getCurrentUserId();
        customer(id);
        var identity = tokens.verifiedGoogle(request.idToken());
        if (!otp.consumeLoginProof(request.loginToken()).getId().equals(id))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify the contact on your current account.");
        saveLink(id, identity);
        return new Status(true, identity.email());
    }

    private User customer(long id) {
        User user = users.findById(id).orElseThrow();
        if (user.getRole().getRoleType() != RoleType.CUSTOMER || !user.isActive()
                || !Boolean.TRUE.equals(user.getIsActive()) || user.getDeletedAt() != null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Google sign-in requires an active customer account.");
        return user;
    }

    private void saveLink(long userId, GoogleIdentity identity) {
        try {
            jdbc.update("INSERT INTO customer_google_identity (user_id,project_id,firebase_uid,google_email) VALUES (?,?,?,?)",
                    userId, identity.project(), identity.uid(), identity.email());
        } catch (DuplicateKeyException e) {
            throw conflict("This Google identity or Nirvana account is already connected.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
