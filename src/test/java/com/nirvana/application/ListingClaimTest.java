package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nirvana.application.otp.*;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.CityPlacesClient;
import com.nirvana.application.service.ListingClaimService;
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

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ListingClaimTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OtpVerificationRepository otps;
    @MockitoBean CityPlacesClient places;

    private static final String PLACE = "{\"id\":\"claim_place_1\",\"primaryType\":\"spa\",\"displayName\":{\"text\":\"Lotus Day Spa\"},"
            + "\"formattedAddress\":\"12 MG Road, Bengaluru 560001, India\",\"internationalPhoneNumber\":\"+91 80 5550 1234\","
            + "\"websiteUri\":\"https://lotus.example/\",\"location\":{\"latitude\":12.9716123456,\"longitude\":77.5946},"
            + "\"addressComponents\":[{\"longText\":\"Bengaluru\",\"shortText\":\"Bengaluru\",\"types\":[\"locality\",\"political\"]},"
            + "{\"longText\":\"Karnataka\",\"shortText\":\"KA\",\"types\":[\"administrative_area_level_1\"]},"
            + "{\"longText\":\"560001\",\"shortText\":\"560001\",\"types\":[\"postal_code\"]},"
            + "{\"longText\":\"India\",\"shortText\":\"IN\",\"types\":[\"country\",\"political\"]}]}";

    private UsernamePasswordAuthenticationToken auth(long id, String role) {
        var p = new UserPrincipal(id, "claim-test", "claim-test@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private long owner() throws Exception {
        String token = UUID.randomUUID().toString();
        var v = new OtpVerification();
        v.setVerificationId(UUID.randomUUID().toString()); v.setEmail(token + "@example.test"); v.setOtpCode("000000");
        v.setExpiresAt(OffsetDateTime.now().plusMinutes(5)); v.setAttempts(0); v.setMaxAttempts(5); v.setVerified(true);
        v.setPurpose(OtpPurpose.REGISTRATION); v.setRegistrationToken(token); v.setRegistrationTokenExpiresAt(OffsetDateTime.now().plusMinutes(5));
        v.setRegistrationConsumed(false); v.setLoginConsumed(false); v.setPasswordResetConsumed(false); otps.saveAndFlush(v);
        String body = json.writeValueAsString(Map.of("accountType", "SPA_OWNER", "registrationToken", token, "fullName", "Claim owner",
                "password", "OwnerTest123!", "acceptPrivacyPolicy", true));
        return json.readTree(mvc.perform(post("/api/auth/register/complete").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("user").get("id").asLong();
    }

    /** The pre-filled form as the owner would submit it after checking each field. */
    private ObjectNode claim(String placeId) throws Exception {
        var form = (ObjectNode) json.readTree(mvc.perform(get("/api/owner/claims/places/" + placeId).with(authentication(auth(1, "ROLE_SPA_OWNER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        form.remove("declaration");
        form.put("name", "Lotus Day Spa & Wellness").put("ownerName", "Asha Rao").put("email", "asha@lotus.example").put("authorised", true);
        return form;
    }

    @Test void customersCannotSearchOrClaim() throws Exception {
        var customer = auth(1, "ROLE_USER");
        mvc.perform(post("/api/owner/claims/search").with(authentication(customer)).contentType("application/json")
                .content("{\"query\":\"Lotus spa Bengaluru\",\"countryCode\":\"IN\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/owner/claims/places/claim_place_1").with(authentication(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/listing-claims").with(authentication(auth(1, "ROLE_SPA_OWNER")))).andExpect(status().isForbidden());
        verifyNoInteractions(places);
    }

    @Test void searchShowsOnlySpasAndMarksListedOnes() throws Exception {
        jdbc.update("INSERT INTO spa (created_at,updated_at,name,google_place_id,commission_pct,default_currency,is_active,is_featured,is_verified,kyc_status,tax_percent,timezone) VALUES (NOW(),NOW(),'Listed','claim_listed',0,'INR',true,false,true,'APPROVED',0,'Asia/Kolkata')");
        when(places.claimSearch("Lotus Bengaluru", "IN")).thenReturn(json.readTree("{\"places\":["
                + "{\"id\":\"claim_place_1\",\"primaryType\":\"spa\",\"displayName\":{\"text\":\"Lotus Day Spa\"}},"
                + "{\"id\":\"claim_listed\",\"primaryType\":\"spa\",\"displayName\":{\"text\":\"Listed\"}},"
                + "{\"id\":\"claim_salon\",\"primaryType\":\"hair_salon\",\"displayName\":{\"text\":\"Lotus Hair & Spa\"}}]}"));
        mvc.perform(post("/api/owner/claims/search").with(authentication(auth(1, "ROLE_SPA_OWNER"))).contentType("application/json")
                        .content("{\"query\":\" Lotus Bengaluru \",\"countryCode\":\"IN\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].alreadyListed").value(false))
                .andExpect(jsonPath("$[1].alreadyListed").value(true));
        mvc.perform(get("/api/owner/claims/places/claim_listed").with(authentication(auth(1, "ROLE_SPA_OWNER")))).andExpect(status().isConflict());
    }

    @Test void prefillComesFromGoogleButNothingIsStored() throws Exception {
        when(places.claimDetails("claim_place_1")).thenReturn(json.readTree(PLACE));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM spa", Long.class);
        mvc.perform(get("/api/owner/claims/places/claim_place_1").with(authentication(auth(1, "ROLE_SPA_OWNER"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.name").value("Lotus Day Spa"))
                .andExpect(jsonPath("$.phone").value("+91 80 5550 1234"))
                .andExpect(jsonPath("$.city").value("Bengaluru"))
                .andExpect(jsonPath("$.state").value("Karnataka"))
                .andExpect(jsonPath("$.postalCode").value("560001"))
                .andExpect(jsonPath("$.countryCode").value("IN"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.latitude").value(12.971612))
                .andExpect(jsonPath("$.termsVersion").value(ListingClaimService.TERMS_VERSION));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM spa", Long.class));
        when(places.claimDetails("claim_salon")).thenReturn(json.readTree("{\"id\":\"claim_salon\",\"primaryType\":\"beauty_salon\"}"));
        mvc.perform(get("/api/owner/claims/places/claim_salon").with(authentication(auth(1, "ROLE_SPA_OWNER")))).andExpect(status().isUnprocessableEntity());
    }

    @Test void ownerSubmitsConfirmedDetailsAndAdminReviews() throws Exception {
        long ownerId = owner();
        var owner = auth(ownerId, "ROLE_SPA_OWNER");
        when(places.claimDetails(anyString())).thenAnswer(i -> json.readTree(PLACE.replace("claim_place_1", i.getArgument(0, String.class))));
        var form = claim("claim_place_1");

        var unconfirmed = form.deepCopy().put("authorised", false);
        mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json").content(unconfirmed.toString())).andExpect(status().isBadRequest());
        var oldTerms = form.deepCopy().put("termsVersion", "partner-old");
        mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json").content(oldTerms.toString())).andExpect(status().isConflict());

        JsonNode created = json.readTree(mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json").content(form.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andReturn().getResponse().getContentAsString());
        long spaId = created.get("spaId").asLong();
        var spa = jdbc.queryForMap("SELECT * FROM spa WHERE id=?", spaId);
        assertEquals("Lotus Day Spa & Wellness", spa.get("name"));
        assertEquals("claim_place_1", spa.get("google_place_id"));
        assertEquals("Asha Rao", spa.get("owner_name"));
        assertEquals("Bengaluru", spa.get("city"));
        assertEquals("OWNER_CONFIRMED", spa.get("geo_source"));
        assertEquals(false, spa.get("is_active"));
        assertEquals(false, spa.get("is_verified"));
        var consent = jdbc.queryForMap("SELECT * FROM spa_listing_claim WHERE spa_id=?", spaId);
        assertEquals(ownerId, ((Number) consent.get("user_id")).longValue());
        assertEquals(ListingClaimService.TERMS_VERSION, consent.get("terms_version"));
        assertEquals(ListingClaimService.DECLARATION, consent.get("declaration"));
        assertNotNull(consent.get("consented_at"));

        // The spa shows in the owner's workspace; a second claim of the same listing is refused.
        mvc.perform(get("/api/onboarding/spas").with(authentication(owner))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(spaId));
        mvc.perform(post("/api/owner/claims").with(authentication(auth(owner(), "ROLE_SPA_OWNER"))).contentType("application/json").content(form.toString())).andExpect(status().isConflict());

        var admin = auth(ownerId, "ROLE_ADMIN");
        long claimId = ((Number) consent.get("id")).longValue();
        mvc.perform(get("/api/admin/listing-claims").with(authentication(admin))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + claimId + ")].spaName").value("Lotus Day Spa & Wellness"))
                .andExpect(jsonPath("$[?(@.id==" + claimId + ")].phone").value("+91 80 5550 1234"))
                .andExpect(jsonPath("$[?(@.id==" + claimId + ")].addressLine").value("12 MG Road, Bengaluru 560001, India"));
        mvc.perform(post("/api/admin/listing-claims/" + claimId + "/approve").with(authentication(admin))).andExpect(status().isOk());
        assertEquals(true, jdbc.queryForObject("SELECT is_active AND is_verified FROM spa WHERE id=?", Boolean.class, spaId));
        mvc.perform(post("/api/admin/listing-claims/" + claimId + "/approve").with(authentication(admin))).andExpect(status().isConflict());

        // Rejecting frees the listing for its real owner.
        var second = claim("claim_place_2");
        long secondSpa = json.readTree(mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json").content(second.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("spaId").asLong();
        long secondClaim = jdbc.queryForObject("SELECT id FROM spa_listing_claim WHERE spa_id=?", Long.class, secondSpa);
        mvc.perform(post("/api/admin/listing-claims/" + secondClaim + "/reject").with(authentication(admin)).contentType("application/json")
                .content("{\"reason\":\"Could not confirm ownership\"}")).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("SELECT google_place_id FROM spa WHERE id=?", String.class, secondSpa));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM spa_listing_claim WHERE id=?", String.class, secondClaim));
        mvc.perform(get("/api/owner/claims/places/claim_place_2").with(authentication(owner))).andExpect(status().isOk());
    }
}
