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
    .andExpect(method(HttpMethod.POST)).andExpect(header("X-Goog-FieldMask","places.id,places.primaryType,places.displayName,nextPageToken"))
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
    .andExpect(jsonPath("$.excludedPrimaryTypes", org.hamcrest.Matchers.hasItems("hair_salon", "beauty_salon", "gym")))
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
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.CsvSource({
   "'  Tattva Spa  ',,Tattva Spa",
   ",Gurgaon,'spas in Gurgaon, IN'",
   "Tattva Spa,Gurgaon,'Tattva Spa in Gurgaon, IN'"
 })
 void sendsTextSearchByNameOrCity(String name, String city, String expected) {
  var mock = new MockServerRestTemplateCustomizer();
  var client = new CityPlacesClient(new RestTemplateBuilder(template -> {
   mock.customize(template);
   mock.getServer(template).expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
    .andExpect(jsonPath("$.textQuery").value(expected))
    .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
  }));
  ReflectionTestUtils.setField(client,"apiKey","test-only");
  ReflectionTestUtils.setField(client,"baseUrl","https://places.googleapis.com/v1");
  client.discover(new com.nirvana.application.model.dto.PlaceDiscoveryRequest(
    com.nirvana.application.model.dto.PlaceDiscoveryRequest.Mode.TEXT, city, name, null, "IN",
    null,null,null,null,null,null));
  mock.getServer().verify();
 }
 @Test void configuredBudgetStopsProviderCalls() {
  var client=new CityPlacesClient(new RestTemplateBuilder());
  ReflectionTestUtils.setField(client,"apiKey","test-only");
  ReflectionTestUtils.setField(client,"maxRequestsPerMinute",0);
  assertEquals(429,assertThrows(ResponseStatusException.class,()->client.search("Delhi","IN",null)).getStatusCode().value());
 }

 /** Fields that move a request into Google's Enterprise (or Atmosphere) price tier. */
 static final java.util.List<String> PRICEY = java.util.List.of("rating", "userRatingCount", "internationalPhoneNumber",
   "websiteUri", "regularOpeningHours", "currentOpeningHours", "reviews", "editorialSummary");

 /** Runs one call and returns the field mask it sent. */
 private static String fieldsSentBy(String path, java.util.function.Consumer<CityPlacesClient> call) {
  var sent = new java.util.concurrent.atomic.AtomicReference<String>();
  var customizer=new MockServerRestTemplateCustomizer();
  var client=new CityPlacesClient(new RestTemplateBuilder(template -> {
   customizer.customize(template);
   customizer.getServer(template).expect(requestTo(org.hamcrest.Matchers.containsString(path)))
    .andExpect(request -> sent.set(request.getHeaders().getFirst("X-Goog-FieldMask")))
    .andRespond(withSuccess("{\"places\":[]}",MediaType.APPLICATION_JSON));
  }));
  ReflectionTestUtils.setField(client,"apiKey","test-only");
  ReflectionTestUtils.setField(client,"baseUrl","https://places.googleapis.com/v1");
  call.accept(client);
  return sent.get();
 }

 @Test void listsAndSpaPhotosStayOutOfGooglesExpensiveTiers() throws Exception {
  var json = new com.fasterxml.jackson.databind.ObjectMapper();
  var text = json.readValue("{\"mode\":\"TEXT\",\"countryCode\":\"IN\",\"city\":\"Pune\"}", com.nirvana.application.model.dto.PlaceDiscoveryRequest.class);
  var nearby = json.readValue("{\"mode\":\"NEARBY\",\"countryCode\":\"IN\",\"latitude\":12.97,\"longitude\":77.59,\"radiusMeters\":3000}", com.nirvana.application.model.dto.PlaceDiscoveryRequest.class);
  var masks = java.util.Map.of(
    "text search", fieldsSentBy("places:searchText", c -> c.discover(text)),
    "nearby search", fieldsSentBy("places:searchNearby", c -> c.discover(nearby)),
    "signed-out nearby", fieldsSentBy("places:searchNearby", c -> c.publicNearby(12.97, 77.59)));
  masks.forEach((what, mask) -> {
   assertNotNull(mask, what);
   assertTrue(mask.contains("places.displayName") && mask.contains("places.photos.name") || what.startsWith("signed-out"), what + ": " + mask);
   for (String field : PRICEY) assertFalse(java.util.Arrays.asList(mask.replace("places.", "").split(",")).contains(field), what + " asks for " + field);
  });
  // Every spa page view: photos and credits only, Google's cheapest Place Details tier.
  assertEquals("id,attributions,photos", fieldsSentBy("/places/ChIJabc", c -> c.photos("ChIJabc")));
 }

 @Test void photoDownloadAcceptsOnlyTheProviderImageHostAndAnImageResponse() {
  byte[] bytes = {1, 2, 3};
  for (boolean allowed : new boolean[]{true, false}) {
   String address = allowed ? "https://lh3.googleusercontent.com/photo" : "https://unrelated.example/photo";
   var calls = new java.util.concurrent.atomic.AtomicInteger();
   var client = new CityPlacesClient(new RestTemplateBuilder(template -> {
    var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(template).build();
    if (calls.getAndIncrement() == 0) {
     server.expect(requestTo("https://places.googleapis.com/v1/places/abc/photos/one/media?maxWidthPx=800&skipHttpRedirect=true"))
      .andRespond(withSuccess("{\"photoUri\":\"" + address + "\"}", MediaType.APPLICATION_JSON));
    } else {
     server.expect(requestTo(address)).andRespond(withSuccess(bytes, MediaType.IMAGE_JPEG));
    }
   }));
   ReflectionTestUtils.setField(client, "apiKey", "test-only");
   ReflectionTestUtils.setField(client, "baseUrl", "https://places.googleapis.com/v1");
   if (allowed) {
    assertArrayEquals(bytes, client.photoImage("abc", "one").getBody());
    assertEquals(2, calls.get());
   } else {
    assertEquals(502, assertThrows(ResponseStatusException.class, () -> client.photoImage("abc", "one")).getStatusCode().value());
    assertEquals(1, calls.get());
   }
  }
 }
}
