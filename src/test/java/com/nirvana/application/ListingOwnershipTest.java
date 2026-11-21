package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.otp.*;
import com.nirvana.application.otp.sender.SmsSender;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.CityPlacesClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Who can see a spa's private details, and who can tie a spa to a Google Maps listing. */
@SpringBootTest(properties = "sms.enabled=true") @AutoConfigureMockMvc @Transactional
class ListingOwnershipTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OtpVerificationRepository otps;
    @MockitoBean CityPlacesClient places;
    @MockitoBean SmsSender sms;
    @Autowired jakarta.persistence.EntityManager em;

    /** Changes a row behind JPA's back, as another request would, and drops cached entities so the next request reloads it. */
    private void sql(String statement, Object... args) {
        em.flush();
        jdbc.update(statement, args);
        em.clear();
    }

    private UsernamePasswordAuthenticationToken auth(long id, String role) {
        var p = new UserPrincipal(id, "own-test", "own-test@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private long owner() throws Exception {
        String token = UUID.randomUUID().toString();
        var v = new OtpVerification();
        v.setVerificationId(UUID.randomUUID().toString()); v.setEmail(token + "@example.test"); v.setOtpCode("000000");
        v.setExpiresAt(OffsetDateTime.now().plusMinutes(5)); v.setAttempts(0); v.setMaxAttempts(5); v.setVerified(true);
        v.setPurpose(OtpPurpose.REGISTRATION); v.setRegistrationToken(token); v.setRegistrationTokenExpiresAt(OffsetDateTime.now().plusMinutes(5));
        v.setRegistrationConsumed(false); v.setLoginConsumed(false); v.setPasswordResetConsumed(false); otps.saveAndFlush(v);
        String body = json.writeValueAsString(Map.of("accountType", "SPA_OWNER", "registrationToken", token, "fullName", "Ownership owner",
                "password", "OwnerTest123!", "acceptPrivacyPolicy", true));
        return json.readTree(mvc.perform(post("/api/auth/register/complete").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("user").get("id").asLong();
    }

    private long spa(String name, boolean published) {
        jdbc.update("INSERT INTO spa (created_at,updated_at,name,gstin,owner_name,commission_pct,default_currency,is_active,is_featured,is_verified,kyc_status,kyc_rejected_reason,tax_percent,timezone) "
                + "VALUES (NOW(),NOW(),?,'29ABCDE1234F1Z5','Private Owner',15,'INR',?,false,?,?,?,0,'Asia/Kolkata')",
                name, published, published, published ? "APPROVED" : "REJECTED", published ? null : "Documents unclear");
        return jdbc.queryForObject("SELECT id FROM spa WHERE name=?", Long.class, name);
    }

    @Test void guestsSeeOnlyPublishedSpasAndNoPrivateBusinessData() throws Exception {
        var guest = auth(1, "ROLE_USER");
        long hidden = spa("Ownership hidden spa", false);
        mvc.perform(get("/api/spas/" + hidden).with(authentication(guest))).andExpect(status().isNotFound());

        long open = spa("Ownership open spa", true);
        mvc.perform(get("/api/spas/" + open).with(authentication(guest))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ownership open spa"))
                .andExpect(jsonPath("$.gstin").doesNotExist())
                .andExpect(jsonPath("$.ownerName").doesNotExist())
                .andExpect(jsonPath("$.commissionPct").doesNotExist())
                .andExpect(jsonPath("$.kycStatus").doesNotExist());

        var admin = auth(1, "ROLE_ADMIN");
        mvc.perform(get("/api/spas/" + hidden).with(authentication(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.gstin").value("29ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.kycRejectedReason").value("Documents unclear"));
    }

    @Test void ownersCannotAttachTheirSpaToAGoogleListing() throws Exception {
        var owner = auth(owner(), "ROLE_SPA_OWNER");
        long spaId = json.readTree(mvc.perform(post("/api/onboarding/spas").with(authentication(owner)).contentType("application/json")
                        .content("{\"name\":\"Ownership scratch spa\",\"phone\":\"1234567890\",\"address\":{\"addressLine\":\"1 Road\",\"city\":\"Delhi\",\"countryCode\":\"IN\",\"timezone\":\"Asia/Kolkata\",\"googlePlaceId\":\"someone_elses_place\"}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertNull(jdbc.queryForObject("SELECT google_place_id FROM spa WHERE id=?", String.class, spaId));
        mvc.perform(put("/api/onboarding/spas/" + spaId + "/address").with(authentication(owner)).contentType("application/json")
                .content("{\"addressLine\":\"2 Road\",\"city\":\"Delhi\",\"countryCode\":\"IN\",\"timezone\":\"Asia/Kolkata\",\"googlePlaceId\":\"someone_elses_place\"}"))
                .andExpect(status().isOk());
        assertNull(jdbc.queryForObject("SELECT google_place_id FROM spa WHERE id=?", String.class, spaId));

        // An address change no longer clears a link that an approved claim or an admin set.
        sql("UPDATE spa SET google_place_id='ownership_linked' WHERE id=?", spaId);
        mvc.perform(put("/api/onboarding/spas/" + spaId + "/address").with(authentication(owner)).contentType("application/json")
                .content("{\"addressLine\":\"3 Road\",\"city\":\"Delhi\",\"countryCode\":\"IN\",\"timezone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isOk());
        assertEquals("ownership_linked", jdbc.queryForObject("SELECT google_place_id FROM spa WHERE id=?", String.class, spaId));
    }

    @Test void onlyAdminsCanImportAGoogleListing() throws Exception {
        mvc.perform(post("/api/onboarding/google-places/any_place").with(authentication(auth(owner(), "ROLE_SPA_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test void codesAreTextedOnlyToUnclaimedSpaListings() throws Exception {
        var owner = auth(owner(), "ROLE_SPA_OWNER");
        when(places.claimDetails("ownership_salon")).thenReturn(json.readTree(
                "{\"id\":\"ownership_salon\",\"primaryType\":\"hair_salon\",\"internationalPhoneNumber\":\"+91 80 5550 1234\"}"));
        mvc.perform(post("/api/owner/claims/places/ownership_salon/phone-code").with(authentication(owner))).andExpect(status().isUnprocessableEntity());

        long listed = spa("Ownership listed spa", true);
        jdbc.update("UPDATE spa SET google_place_id='ownership_listed' WHERE id=?", listed);
        mvc.perform(post("/api/owner/claims/places/ownership_listed/phone-code").with(authentication(owner))).andExpect(status().isConflict());
        verify(places, never()).claimDetails("ownership_listed");
        verifyNoInteractions(sms);
    }

    @Test void aClaimCannotBeApprovedOnceTheSpaNoLongerHoldsTheListing() throws Exception {
        long ownerId = owner();
        var owner = auth(ownerId, "ROLE_SPA_OWNER");
        when(places.claimDetails(anyString())).thenAnswer(i -> json.readTree(("{\"id\":\"%s\",\"primaryType\":\"spa\",\"displayName\":{\"text\":\"Lotus\"},"
                + "\"formattedAddress\":\"12 MG Road\",\"internationalPhoneNumber\":\"+91 80 5550 1234\","
                + "\"addressComponents\":[{\"longText\":\"Bengaluru\",\"shortText\":\"Bengaluru\",\"types\":[\"locality\"]},"
                + "{\"longText\":\"India\",\"shortText\":\"IN\",\"types\":[\"country\"]}]}").formatted(i.getArgument(0, String.class))));
        var form = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(mvc.perform(get("/api/owner/claims/places/ownership_claimed").with(authentication(owner)))
                .andReturn().getResponse().getContentAsString());
        form.remove("declaration"); form.remove("listingHasPhone");
        form.put("ownerName", "Asha Rao").put("authorised", true);
        long spaId = json.readTree(mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json").content(form.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("spaId").asLong();
        long claimId = jdbc.queryForObject("SELECT id FROM spa_listing_claim WHERE spa_id=?", Long.class, spaId);
        sql("UPDATE spa SET google_place_id='ownership_swapped' WHERE id=?", spaId);
        mvc.perform(post("/api/admin/listing-claims/" + claimId + "/approve").with(authentication(auth(ownerId, "ROLE_ADMIN"))))
                .andExpect(status().isConflict());
        assertEquals(false, jdbc.queryForObject("SELECT is_active FROM spa WHERE id=?", Boolean.class, spaId));
    }
}
