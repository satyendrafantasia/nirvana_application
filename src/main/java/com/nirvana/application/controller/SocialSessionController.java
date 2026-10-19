package com.nirvana.application.controller;
import com.nirvana.application.model.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.List;
@RestController @RequestMapping("/api/auth/oauth2") @RequiredArgsConstructor
public class SocialSessionController {
 private final OAuth2ClientProperties properties;
 private final ClientRegistrationRepository registrations;
 @GetMapping("/providers") public List<String> providers() {
  return properties.getRegistration().keySet().stream().filter(id -> registrations.findByRegistrationId(id) != null).sorted().toList();
 }
 @PostMapping("/session") public ResponseEntity<AuthResponse> exchange(HttpServletRequest request) {
  var session = request.getSession(false);
  if (session == null) return ResponseEntity.status(401).build();
  synchronized(session) {
   var result = session.getAttribute("SPA_OAUTH_RESULT");
   var expires = session.getAttribute("SPA_OAUTH_EXPIRES");
   session.removeAttribute("SPA_OAUTH_RESULT"); session.removeAttribute("SPA_OAUTH_EXPIRES");
   if (!(result instanceof AuthResponse auth) || !(expires instanceof Long deadline) || deadline < System.currentTimeMillis()) return ResponseEntity.status(401).build();
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth);
  }
 }
}
