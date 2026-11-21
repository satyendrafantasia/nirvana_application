package com.nirvana.application.controller;

import com.nirvana.application.service.SpaBookingReplies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The page a spa opens from its booking SMS. No sign-in: the private link is the credential, as
 * most spas listed from Google Maps have no Nirvana account.
 */
@RestController
@RequestMapping("/api/public/spa-bookings/{token}")
@RequiredArgsConstructor
public class SpaBookingReplyController {
    private final SpaBookingReplies replies;

    public record DeclineRequest(@Size(max = 500) String reason) {}

    @GetMapping
    public ResponseEntity<SpaBookingReplies.View> view(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(replies.view(token));
    }

    @PostMapping("/confirm")
    public SpaBookingReplies.View confirm(@PathVariable String token) {
        return replies.confirm(token);
    }

    @PostMapping("/decline")
    public SpaBookingReplies.View decline(@PathVariable String token, @Valid @RequestBody(required = false) DeclineRequest request) {
        return replies.decline(token, request == null ? null : request.reason());
    }
}
