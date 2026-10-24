package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.service.CityPlacesClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class CityDiscoveryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CityPlacesClient places;
    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken("test", null, List.of(new SimpleGrantedAuthority(role)));
    }
    @Test void onlyAdminsCanReadAndImport() throws Exception {
        for (String role : List.of("ROLE_USER","ROLE_SPA_OWNER","ROLE_SPA_MANAGER")) {
            mvc.perform(get("/api/admin/city-discovery/cities").with(authentication(auth(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/city-discovery/search").with(authentication(auth(role))).contentType("application/json").content("{\"city\":\"Test City\",\"countryCode\":\"IN\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(places);
    }
    @Test void repeatPagesDeduplicateAndPersistOnlyReferences() throws Exception {
        when(places.search("discovery test city","IN",null)).thenReturn(json.readTree("{\"places\":[{\"id\":\"test_place_123\",\"displayName\":{\"text\":\"Never store this name\"}},{\"id\":\"test_place_123\"}],\"nextPageToken\":\"next\"}"));
        for(int i=0;i<2;i++) mvc.perform(post("/api/admin/city-discovery/search").with(authentication(auth("ROLE_PLATFORM_ADMIN"))).contentType("application/json").content("{\"city\":\" Discovery  Test City \",\"countryCode\":\"in\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.found").value(1)).andExpect(jsonPath("$.nextPageToken").value("next"));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM city_place_discovery WHERE city='discovery test city'",Long.class));
        mvc.perform(get("/api/admin/city-discovery/places?city=DISCOVERY%20TEST%20CITY&countryCode=IN").with(authentication(auth("ROLE_ADMIN")))).andExpect(status().isOk());
        when(places.details("test_place_123")).thenReturn(json.readTree("{\"displayName\":{\"text\":\"Live business\"}}"));
        mvc.perform(get("/api/admin/city-discovery/places/test_place_123").with(authentication(auth("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.displayName.text").value("Live business"));
    }
    @Test void invalidInputsAndUnknownPlaceDoNotCallProvider() throws Exception {
        mvc.perform(post("/api/admin/city-discovery/search").with(authentication(auth("ROLE_ADMIN"))).contentType("application/json").content("{\"city\":\" \",\"countryCode\":\"India\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/city-discovery/places/unknown_place").with(authentication(auth("ROLE_ADMIN")))).andExpect(status().isNotFound());
        verifyNoInteractions(places);
    }

    @Test void liveDiscoveryRequiresLoginAndValidCoordinates() throws Exception {
        mvc.perform(post("/api/discovery/search").contentType("application/json").content("{\"mode\":\"TEXT\",\"city\":\"Delhi\",\"countryCode\":\"IN\"}")).andExpect(status().isUnauthorized());
        for(String body : List.of(
            "{\"mode\":\"NEARBY\",\"countryCode\":\"IN\",\"latitude\":91,\"longitude\":77,\"radiusMeters\":3000}",
            "{\"mode\":\"NEARBY\",\"countryCode\":\"IN\",\"latitude\":12,\"longitude\":77,\"radiusMeters\":50001}",
            "{\"mode\":\"NEARBY\",\"countryCode\":\"IN\",\"radiusMeters\":3000}",
            "{\"mode\":\"TEXT\",\"city\":\"Delhi\",\"locality\":\"Saket\",\"countryCode\":\"IN\"}"))
            mvc.perform(post("/api/discovery/search").with(authentication(auth("ROLE_USER"))).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(places);
    }
    @Test void liveResultsDeduplicateWithoutSavingAndDetailsAreNoStore() throws Exception {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM city_place_discovery",Long.class);
        when(places.discover(any())).thenReturn(json.readTree("{\"places\":[{\"id\":\"discovery_live_only\",\"displayName\":{\"text\":\"Live spa\"}},{\"id\":\"discovery_live_only\"}],\"nextPageToken\":\"next\"}"));
        mvc.perform(post("/api/discovery/search").with(authentication(auth("ROLE_USER"))).contentType("application/json").content("{\"mode\":\"TEXT\",\"city\":\"Delhi\",\"countryCode\":\"IN\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.places.length()").value(1)).andExpect(jsonPath("$.places[0].nirvanaSpaId").doesNotExist()).andExpect(jsonPath("$.nextPageToken").value("next"));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM city_place_discovery",Long.class));
        when(places.details("discovery_live_only")).thenReturn(json.readTree("{\"id\":\"discovery_live_only\"}"));
        mvc.perform(get("/api/discovery/places/discovery_live_only").with(authentication(auth("ROLE_USER")))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void onlyAdminCanSaveReviewedReferences() throws Exception {
        String body="{\"city\":\"Reviewed Test\",\"countryCode\":\"IN\",\"placeIds\":[\"reviewed_123\",\"reviewed_123\"]}";
        mvc.perform(post("/api/admin/city-discovery/references").with(authentication(auth("ROLE_USER"))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        for(int i=0;i<2;i++) mvc.perform(post("/api/admin/city-discovery/references").with(authentication(auth("ROLE_ADMIN"))).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM city_place_discovery WHERE city='reviewed test'",Long.class));
    }

    @Test void bookingLinksRequireActiveVerifiedAndOperationalSpa() throws Exception {
        jdbc.update("INSERT INTO spa (created_at,updated_at,name,google_place_id,commission_pct,default_currency,is_active,is_featured,is_verified,kyc_status,tax_percent) VALUES (NOW(),NOW(),'Discovery fixture','discovery_bookable_test',0,'INR',true,false,true,'VERIFIED',0)");
        Long id=jdbc.queryForObject("SELECT id FROM spa WHERE google_place_id='discovery_bookable_test'",Long.class);
        String request="{\"mode\":\"TEXT\",\"city\":\"Delhi\",\"countryCode\":\"IN\"}";
        when(places.discover(any())).thenReturn(json.readTree("{\"places\":[{\"id\":\"discovery_bookable_test\",\"businessStatus\":\"OPERATIONAL\"}]}"));
        mvc.perform(post("/api/discovery/search").with(authentication(auth("ROLE_USER"))).contentType("application/json").content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.places[0].nirvanaSpaId").value(id));
        jdbc.update("UPDATE spa SET is_active=false WHERE id=?",id);
        mvc.perform(post("/api/discovery/search").with(authentication(auth("ROLE_USER"))).contentType("application/json").content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.places[0].nirvanaSpaId").doesNotExist());
        jdbc.update("UPDATE spa SET is_active=true,is_verified=false WHERE id=?",id);
        mvc.perform(post("/api/discovery/search").with(authentication(auth("ROLE_USER"))).contentType("application/json").content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.places[0].nirvanaSpaId").doesNotExist());
        jdbc.update("UPDATE spa SET is_verified=true WHERE id=?",id);
        when(places.discover(any())).thenReturn(json.readTree("{\"places\":[{\"id\":\"discovery_bookable_test\",\"businessStatus\":\"CLOSED_TEMPORARILY\"}]}"));
        mvc.perform(post("/api/discovery/search").with(authentication(auth("ROLE_USER"))).contentType("application/json").content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.places[0].nirvanaSpaId").doesNotExist());
    }
}
