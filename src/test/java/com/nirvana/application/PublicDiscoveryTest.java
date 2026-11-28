package com.nirvana.application;

import com.nirvana.application.service.CityPlacesClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The key is blanked so these tests can never reach Google, whatever the machine's environment holds. */
@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(properties = "google.places.api-key=")
class PublicDiscoveryTest {
    @Autowired MockMvc mvc;

    @Test void signedOutVisitorsReachThePublicEndpointsButNotTheFullSearch() throws Exception {
        mvc.perform(get("/api/public/discovery/status")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.configured").value(false));
        // 503 (no key) rather than 401 shows the request got past sign-in.
        mvc.perform(post("/api/public/discovery/nearby").contentType("application/json")
                .content("{\"latitude\":12.97,\"longitude\":77.59}")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/discovery/search").contentType("application/json")
                .content("{\"mode\":\"NEARBY\",\"countryCode\":\"IN\",\"latitude\":12.97,\"longitude\":77.59,\"radiusMeters\":5000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void badCoordinatesAreRefused() throws Exception {
        mvc.perform(post("/api/public/discovery/nearby").contentType("application/json")
                .content("{\"latitude\":120,\"longitude\":77.59}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/public/discovery/nearby").contentType("application/json")
                .content("{\"longitude\":77.59}")).andExpect(status().isBadRequest());
    }

    @Test void theProviderCallIsFixedAndHasItsOwnBudget() {
        var mock = new MockServerRestTemplateCustomizer();
        var client = new CityPlacesClient(new RestTemplateBuilder(template -> {
            mock.customize(template);
            mock.getServer(template).expect(requestTo("https://places.googleapis.com/v1/places:searchNearby"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-Goog-FieldMask", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("photos"))))
                    .andExpect(jsonPath("$.excludedTypes", org.hamcrest.Matchers.hasItems("beauty_salon", "medical_clinic", "store")))
                    .andExpect(content().json("{\"includedPrimaryTypes\":[\"spa\",\"massage_spa\"],\"maxResultCount\":6,\"rankPreference\":\"DISTANCE\",\"locationRestriction\":{\"circle\":{\"center\":{\"latitude\":12.97,\"longitude\":77.59},\"radius\":5000}}}"))
                    .andRespond(withSuccess("{\"places\":[]}", MediaType.APPLICATION_JSON));
        }));
        ReflectionTestUtils.setField(client, "apiKey", "test-only");
        ReflectionTestUtils.setField(client, "baseUrl", "https://places.googleapis.com/v1");
        ReflectionTestUtils.setField(client, "publicRequestsPerMinute", 1);
        client.publicNearby(12.97, 77.59);
        mock.getServer().verify();
        // The public budget is used up; signed-in searches still have theirs.
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> client.publicNearby(12.97, 77.59)).getStatusCode().value());
    }
}
