package com.nirvana.application;
import com.nirvana.application.controller.SocialSessionController;
import com.nirvana.application.config.SecurityConfig;
import com.nirvana.application.model.dto.AuthResponse;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialSessionTest {
 private final SocialSessionController controller = new SocialSessionController(new OAuth2ClientProperties(), id -> null);
 @Test void exchangeIsBoundToSessionAndSingleUse() {
  var request=new MockHttpServletRequest();
  request.getSession().setAttribute("SPA_OAUTH_RESULT",AuthResponse.builder().accessToken("test-access").build());
  request.getSession().setAttribute("SPA_OAUTH_EXPIRES",System.currentTimeMillis()+60000);
  assertEquals(401,controller.exchange(new MockHttpServletRequest()).getStatusCode().value());
  var response=controller.exchange(request);assertEquals(200,response.getStatusCode().value());assertEquals("no-store",response.getHeaders().getCacheControl());
  assertEquals(401,controller.exchange(request).getStatusCode().value());
 }
 @Test void expiredExchangeCannotIssueCredentials() {
  var request=new MockHttpServletRequest();request.getSession().setAttribute("SPA_OAUTH_RESULT",AuthResponse.builder().accessToken("test-access").build());request.getSession().setAttribute("SPA_OAUTH_EXPIRES",System.currentTimeMillis()-1);
  assertEquals(401,controller.exchange(request).getStatusCode().value());assertNull(request.getSession().getAttribute("SPA_OAUTH_RESULT"));
 }
 @Test void blankProviderCredentialsDoNotExposeBrokenLoginButtons() {
  var props=new OAuth2ClientProperties();var google=new OAuth2ClientProperties.Registration();google.setClientId("");google.setClientSecret("");props.getRegistration().put("google",google);
  var config=new SecurityConfig(null,null,null,null,null);var repo=config.clientRegistrationRepository(props);
  assertTrue(new SocialSessionController(props,repo).providers().isEmpty());
 }
 @Test void configuredGoogleUsesProviderDefaultsAndHandledCallback() {
  var props=new OAuth2ClientProperties();var google=new OAuth2ClientProperties.Registration();google.setClientId("test-id");google.setClientSecret("test-secret");props.getRegistration().put("google",google);
  var repo=new SecurityConfig(null,null,null,null,null).clientRegistrationRepository(props);var registration=repo.findByRegistrationId("google");
  assertEquals("{baseUrl}/oauth2/callback/{registrationId}",registration.getRedirectUri());assertTrue(registration.getProviderDetails().getAuthorizationUri().startsWith("https://accounts.google.com"));assertEquals(1,new SocialSessionController(props,repo).providers().size());
 }
}
