package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nirvana.application.otp.*;
import com.nirvana.application.otp.sender.SmsSender;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.CityPlacesClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "sms.enabled=true") @AutoConfigureMockMvc @Transactional
class ListingClaimPhoneTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OtpVerificationRepository otps;
    @MockitoBean CityPlacesClient places;
    @MockitoBean SmsSender sms;

    private static final String PLACE = "{\"id\":\"%s\",\"primaryType\":\"spa\",\"displayName\":{\"text\":\"Lotus Day Spa\"},"
            + "\"formattedAddress\":\"12 MG Road, Bengaluru\",\"internationalPhoneNumber\":\"+91 80 5550 1234\","
            + "\"addressComponents\":[{\"longText\":\"Bengaluru\",\"shortText\":\"Bengaluru\",\"types\":[\"locality\"]},"
            + "{\"longText\":\"India\",\"shortText\":\"IN\",\"types\":[\"country\"]}]}";

    private UsernamePasswordAuthenticationToken auth(long id) {
        var p = new UserPrincipal(id, "phone-test", "phone-test@example.test", "", true, List.of(new SimpleGrantedAuthority("ROLE_SPA_OWNER")));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private long owner() throws Exception {
        String token = UUID.randomUUID().toString();
        var v = new OtpVerification();
        v.setVerificationId(UUID.randomUUID().toString()); v.setEmail(token + "@example.test"); v.setOtpCode("000000");
        v.setExpiresAt(OffsetDateTime.now().plusMinutes(5)); v.setAttempts(0); v.setMaxAttempts(5); v.setVerified(true);
        v.setPurpose(OtpPurpose.REGISTRATION); v.setRegistrationToken(token); v.setRegistrationTokenExpiresAt(OffsetDateTime.now().plusMinutes(5));
        v.setRegistrationConsumed(false); v.setLoginConsumed(false); v.setPasswordResetConsumed(false); otps.saveAndFlush(v);
        String body = json.writeValueAsString(Map.of("accountType", "SPA_OWNER", "registrationToken", token, "fullName", "Phone owner",
                "password", "OwnerTest123!", "acceptPrivacyPolicy", true));
        return json.readTree(mvc.perform(post("/api/auth/register/complete").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("user").get("id").asLong();
    }

    private String send(UsernamePasswordAuthenticationToken who, String placeId) throws Exception {
        return json.readTree(mvc.perform(post("/api/owner/claims/places/" + placeId + "/phone-code").with(authentication(who)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+91 •• •••• 1234"))
                .andReturn().getResponse().getContentAsString()).get("verificationId").asText();
    }

    private String lastCode() {
        var message = ArgumentCaptor.forClass(String.class);
        verify(sms, atLeastOnce()).sendOtpSms(eq("+918055501234"), message.capture());
        var m = Pattern.compile("\\b(\\d{6})\\b").matcher(message.getValue());
        assertTrue(m.find(), message.getValue());
        return m.group(1);
    }

    private org.springframework.test.web.servlet.ResultActions check(UsernamePasswordAuthenticationToken who, String id, String code) throws Exception {
        return mvc.perform(post("/api/owner/claims/phone-code/verify").with(authentication(who)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("verificationId", id, "code", code))));
    }

    private ObjectNode claim(UsernamePasswordAuthenticationToken who, String placeId, String verificationId) throws Exception {
        var form = (ObjectNode) json.readTree(mvc.perform(get("/api/owner/claims/places/" + placeId).with(authentication(who)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listingHasPhone").value(true)).andReturn().getResponse().getContentAsString());
        form.remove("declaration"); form.remove("listingHasPhone");
        form.put("ownerName", "Asha Rao").put("authorised", true);
        if (verificationId != null) form.put("phoneVerificationId", verificationId);
        return form;
    }

    @Test void codeGoesToTheListingPhoneAndMarksTheClaimVerified() throws Exception {
        when(places.claimDetails(anyString())).thenAnswer(i -> json.readTree(PLACE.formatted(i.getArgument(0, String.class))));
        var owner = auth(owner());
        String id = send(owner, "phone_place_1");
        String code = lastCode();
        assertNotEquals(code, jdbc.queryForObject("SELECT code_hash FROM listing_claim_phone_check WHERE verification_id=?", String.class, id));

        String wrong = code.equals("000000") ? "111111" : "000000";
        check(owner, id, wrong).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("4 tries left")));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM listing_claim_phone_check WHERE verification_id=?", Integer.class, id));
        check(auth(owner()), id, code).andExpect(status().isNotFound());
        mvc.perform(post("/api/owner/claims/places/phone_place_1/phone-code").with(authentication(owner))).andExpect(status().isTooManyRequests());
        check(owner, id, code).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));

        // The verification only counts for the listing it was sent for, and only once.
        mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json")
                .content(claim(owner, "phone_place_2", id).toString())).andExpect(status().isConflict());
        long spaId = json.readTree(mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json")
                        .content(claim(owner, "phone_place_1", id).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phoneVerified").value(true)).andReturn().getResponse().getContentAsString()).get("spaId").asLong();
        assertNotNull(jdbc.queryForObject("SELECT phone_verified_at FROM spa_listing_claim WHERE spa_id=?", Object.class, spaId));
        mvc.perform(get("/api/admin/listing-claims").with(authentication(new UsernamePasswordAuthenticationToken("a", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.spaId==" + spaId + ")].phoneVerifiedAt").isNotEmpty());

        // Skipping verification still submits, unverified, for the admin to call.
        long other = json.readTree(mvc.perform(post("/api/owner/claims").with(authentication(owner)).contentType("application/json")
                        .content(claim(owner, "phone_place_3", null).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phoneVerified").value(false)).andReturn().getResponse().getContentAsString()).get("spaId").asLong();
        assertNull(jdbc.queryForObject("SELECT phone_verified_at FROM spa_listing_claim WHERE spa_id=?", Object.class, other));
    }

    @Test void tooManyWrongCodesLocksTheCode() throws Exception {
        when(places.claimDetails(anyString())).thenAnswer(i -> json.readTree(PLACE.formatted(i.getArgument(0, String.class))));
        var owner = auth(owner());
        String id = send(owner, "phone_place_lock");
        String code = lastCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) check(owner, id, wrong).andExpect(status().isUnprocessableEntity());
        check(owner, id, code).andExpect(status().isTooManyRequests());
    }

    @Test void listingWithoutAPhoneCannotBeTexted() throws Exception {
        when(places.claimDetails("phone_none")).thenReturn(json.readTree("{\"id\":\"phone_none\",\"primaryType\":\"spa\"}"));
        mvc.perform(post("/api/owner/claims/places/phone_none/phone-code").with(authentication(auth(owner()))))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(sms);
    }
}
