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
}
