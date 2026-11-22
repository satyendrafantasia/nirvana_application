package com.nirvana.application;

import com.nirvana.application.auth.service.FirebaseEmailLinks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FirebaseEmailLinksTest {
    MockRestServiceServer server;
    FirebaseEmailLinks links;
    @BeforeEach void setup() {
        links = new FirebaseEmailLinks(new RestTemplateBuilder().additionalCustomizers(
                template -> server = MockRestServiceServer.bindTo(template).build()), "test-key");
    }
    @Test void exchangesCodeAndConfirmsReturnedEmail() {
        server.expect(requestTo("https://identitytoolkit.googleapis.com/v1/accounts:signInWithEmailLink?key=test-key"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"email\":\"Person@example.test\",\"oobCode\":\"one-time-code\"}"))
                .andRespond(withSuccess("{\"idToken\":\"firebase-session\",\"localId\":\"uid\",\"email\":\"person@example.test\"}", MediaType.APPLICATION_JSON));
        assertEquals("person@example.test", links.redeem("Person@example.test", "one-time-code"));
        server.verify();
    }
    @Test void rejectsExpiredOrReusedCode() {
        server.expect(method(HttpMethod.POST)).andRespond(withBadRequest());
        assertThrows(ResponseStatusException.class, () -> links.redeem("user@example.test","used"));
    }
    @Test void requiresCompletedFirebaseAuthentication() {
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess(
                "{\"localId\":\"uid\",\"email\":\"user@example.test\",\"mfaPendingCredential\":\"pending\"}", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> links.redeem("user@example.test","code"));
    }
    @Test void rejectsMismatchedAddress() {
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess(
                "{\"idToken\":\"firebase-session\",\"localId\":\"uid\",\"email\":\"other@example.test\"}", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> links.redeem("user@example.test","code"));
    }
}
