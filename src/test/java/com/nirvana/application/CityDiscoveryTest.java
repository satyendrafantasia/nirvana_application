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
}
