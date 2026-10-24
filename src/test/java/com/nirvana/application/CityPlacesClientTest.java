package com.nirvana.application;
import com.nirvana.application.service.CityPlacesClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CityPlacesClientTest {
 @Test void sendsBoundedSearchWithPageTokenAndMinimalFields() {
  var customizer=new MockServerRestTemplateCustomizer();
  var client=new CityPlacesClient(new RestTemplateBuilder(customizer));
  ReflectionTestUtils.setField(client,"apiKey","test-only");
  ReflectionTestUtils.setField(client,"baseUrl","https://places.googleapis.com/v1");
  // Expectations are installed on each template as it is built.
  var builder=new RestTemplateBuilder(template -> {
   customizer.customize(template);
   customizer.getServer(template).expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
    .andExpect(method(HttpMethod.POST)).andExpect(header("X-Goog-FieldMask","places.id,nextPageToken"))
    .andExpect(content().json("{\"textQuery\":\"spas in Bengaluru, IN\",\"includedType\":\"spa\",\"strictTypeFiltering\":true,\"pageSize\":20,\"regionCode\":\"IN\",\"pageToken\":\"next\"}"))
    .andRespond(withSuccess("{\"places\":[{\"id\":\"abc\"}]}",MediaType.APPLICATION_JSON));
  });
  ReflectionTestUtils.setField(client,"builder",builder);
  assertEquals("abc",client.search("Bengaluru","IN","next").path("places").get(0).path("id").asText());
  customizer.getServer().verify();
 }
 @Test void missingKeyFailsBeforeNetwork() {
  var client=new CityPlacesClient(new RestTemplateBuilder());
  var ex=assertThrows(ResponseStatusException.class,()->client.search("Delhi","IN",null));
  assertEquals(503,ex.getStatusCode().value());
 }

 @Test void nearbyUsesRadiusRankAndSummaryFieldsWithoutPagination() throws Exception {
  var customizer=new MockServerRestTemplateCustomizer();
  var client=new CityPlacesClient(new RestTemplateBuilder(template -> {
   customizer.customize(template);
   customizer.getServer(template).expect(requestTo("https://places.googleapis.com/v1/places:searchNearby"))
    .andExpect(method(HttpMethod.POST))
    .andExpect(header("X-Goog-FieldMask", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("websiteUri"))))
    .andExpect(content().json("{\"includedTypes\":[\"spa\"],\"maxResultCount\":20,\"rankPreference\":\"DISTANCE\",\"locationRestriction\":{\"circle\":{\"center\":{\"latitude\":12.97,\"longitude\":77.59},\"radius\":3000}}}"))
    .andRespond(withSuccess("{\"places\":[]}",MediaType.APPLICATION_JSON));
  }));
  ReflectionTestUtils.setField(client,"apiKey","test-only");
  ReflectionTestUtils.setField(client,"baseUrl","https://places.googleapis.com/v1");
  var request = new com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"mode\":\"NEARBY\",\"countryCode\":\"IN\",\"latitude\":12.97,\"longitude\":77.59,\"radiusMeters\":3000}",com.nirvana.application.model.dto.PlaceDiscoveryRequest.class);
  client.discover(request); customizer.getServer().verify();
 }
 @Test void localityUsesRestrictionAndKeepsPaginationParameters() throws Exception {
  var customizer=new MockServerRestTemplateCustomizer();
  var client=new CityPlacesClient(new RestTemplateBuilder(template -> {
   customizer.customize(template);
   customizer.getServer(template).expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
    .andExpect(content().json("{\"textQuery\":\"spas in Indiranagar, Bengaluru, IN\",\"pageToken\":\"next\",\"locationRestriction\":{\"rectangle\":{\"low\":{\"latitude\":12.9,\"longitude\":77.5},\"high\":{\"latitude\":13.0,\"longitude\":77.7}}}}"))
    .andRespond(withSuccess("{\"places\":[]}",MediaType.APPLICATION_JSON));
  }));
  ReflectionTestUtils.setField(client,"apiKey","test-only");
  ReflectionTestUtils.setField(client,"baseUrl","https://places.googleapis.com/v1");
  var request = new com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"mode\":\"TEXT\",\"city\":\"Bengaluru\",\"locality\":\"Indiranagar\",\"countryCode\":\"IN\",\"pageToken\":\"next\",\"bounds\":{\"low\":{\"latitude\":12.9,\"longitude\":77.5},\"high\":{\"latitude\":13.0,\"longitude\":77.7}}}",com.nirvana.application.model.dto.PlaceDiscoveryRequest.class);
  client.discover(request); customizer.getServer().verify();
 }
 @Test void configuredBudgetStopsProviderCalls() {
  var client=new CityPlacesClient(new RestTemplateBuilder());
  ReflectionTestUtils.setField(client,"apiKey","test-only");
  ReflectionTestUtils.setField(client,"maxRequestsPerMinute",0);
  assertEquals(429,assertThrows(ResponseStatusException.class,()->client.search("Delhi","IN",null)).getStatusCode().value());
 }
}
