package com.nirvana.application.controller;

import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.AuthService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;

/** The signed-in customer's own profile, password and loyalty balance. */
@RestController
@RequestMapping("/api/me/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserRepository users;
    private final AuthService authService;

    /** Email and phone are shown but not editable here: changing them needs verification. */
    public record AccountView(Long id, String email, String phone, String firstName, String lastName,
                              String displayName, String timezone, String locale, boolean marketingOptIn,
                              int loyaltyPoints, boolean emailVerified, boolean phoneVerified) {
    }

    public record ProfileInput(
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 100) String displayName,
            @NotBlank @Size(max = 64) String timezone,
            @Pattern(regexp = "[a-z]{2,3}([-_][A-Za-z]{2,4})?", message = "must be a language code such as en or en-IN") String locale,
            @NotNull Boolean marketingOptIn) {
    }

    /** bcrypt only uses the first 72 bytes, so longer passwords are refused rather than silently cut. */
    public record PasswordInput(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public AccountView get() {
        return view(current());
    }

    @PutMapping
    @Transactional
    public AccountView update(@Valid @RequestBody ProfileInput input) {
        try {
            ZoneId.of(input.timezone().trim());
        } catch (DateTimeException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid time zone.");
        }
        User user = current();
        user.setName(input.firstName().trim());
        user.setLastName(blankToNull(input.lastName()));
        user.setDisplayName(blankToNull(input.displayName()));
        user.setTimezone(input.timezone().trim());
        user.setLocale(blankToNull(input.locale()));
        user.setMarketingOptIn(input.marketingOptIn());
        return view(users.save(user));
    }

    /** On success every other device is signed out; this device gets a new session. */
    @PostMapping("/password")
    public AuthResponse changePassword(@Valid @RequestBody PasswordInput input) {
        return authService.changePassword(SecurityUtils.getCurrentUserId(), input.currentPassword(), input.newPassword());
    }

    private User current() {
        Long id = SecurityUtils.getCurrentUserId();
        return users.findById(id).orElseThrow(() -> new EntityNotFoundException("Account not found"));
    }

    private AccountView view(User u) {
        return new AccountView(u.getId(), u.getEmail(), u.getPhone(), u.getName(), u.getLastName(), u.getDisplayName(),
                u.getTimezone(), u.getLocale(), Boolean.TRUE.equals(u.getMarketingOptIn()),
                u.getLoyaltyPoints() == null ? 0 : u.getLoyaltyPoints(),
                Boolean.TRUE.equals(u.getEmailVerified()), Boolean.TRUE.equals(u.getPhoneVerified()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
