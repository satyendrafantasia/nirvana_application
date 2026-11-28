package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.ListingClaimPhoneService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class StorefrontController {
    private final JdbcTemplate db;
    private final ListingClaimPhoneService proofs;

    private record Claim(long id, long owner, String name, String place, String phone, boolean enabled) {}
    public record PublicView(long spaId, String name, String whatsappNumber) {}
    public record Settings(String name, String placeId, String verifiedNumber, boolean enabled) {}
    public record Update(@NotNull Boolean enabled, @Size(max=64) String verificationId) {}

    private Claim claim(long spa, boolean lock) {
        return db.query("""
                SELECT c.id, c.user_id, s.name, c.google_place_id,
                       (SELECT p.phone_number FROM listing_claim_phone_check p
                        WHERE p.verification_id=COALESCE(c.enquiry_verification_id,c.phone_verification_id)
                          AND p.user_id=c.user_id AND p.google_place_id=c.google_place_id
                          AND p.verified_at IS NOT NULL AND p.consumed_at IS NOT NULL) AS phone,
                       c.enquiries_enabled
                FROM spa s JOIN spa_manager m ON m.id=s.spa_manager_id
                JOIN spa_listing_claim c ON c.spa_id=s.id AND c.google_place_id=s.google_place_id AND c.user_id=m.user_id
                WHERE s.id=? AND s.is_active AND s.is_verified AND c.status='APPROVED'
                ORDER BY c.id DESC LIMIT 1
                """ + (lock ? " FOR UPDATE" : ""), (rs,i) -> {
                    String phone=rs.getString("phone");
                    if (phone != null && !phone.matches("[+][1-9][0-9]{7,14}")) phone=null;
                    return new Claim(rs.getLong("id"), rs.getLong("user_id"), rs.getString("name"),
                            rs.getString("google_place_id"), phone, rs.getBoolean("enquiries_enabled"));
                }, spa).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Storefront unavailable"));
    }

    @GetMapping("/api/spas/{spa}/storefront")
    public PublicView storefront(@PathVariable long spa) {
        var c=claim(spa,false);
        return new PublicView(spa,c.name(),c.enabled() ? c.phone() : null);
    }

    private Claim own(long spa, boolean lock) {
        var c=claim(spa,lock);
        if (c.owner()!=SecurityUtils.getCurrentUserId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return c;
    }

    @GetMapping("/api/owner/spas/{spa}/storefront")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public Settings settings(@PathVariable long spa) {
        var c=own(spa,false);
        return new Settings(c.name(),c.place(),c.phone(),c.enabled() && c.phone()!=null);
    }

    @PutMapping("/api/owner/spas/{spa}/storefront")
    @PreAuthorize("@staffAccess.spa(#spa)")
    @Transactional
    public Settings update(@PathVariable long spa,@Valid @RequestBody Update update) {
        var c=own(spa,true);
        if (update.enabled() && update.verificationId()!=null) {
            proofs.consume(c.owner(),c.place(),update.verificationId());
            db.update("UPDATE spa_listing_claim SET enquiry_verification_id=? WHERE id=?",update.verificationId(),c.id());
            c=own(spa,false);
        }
        if (update.enabled() && c.phone()==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Verify the listing phone before enabling enquiries.");
        db.update("UPDATE spa_listing_claim SET enquiries_enabled=? WHERE id=?",update.enabled(),c.id());
        return new Settings(c.name(),c.place(),c.phone(),update.enabled());
    }
}
