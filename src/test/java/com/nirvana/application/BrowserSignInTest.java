package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.otp.sender.EmailSender;
import com.nirvana.application.repository.RoleRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.BrowserSession;
import com.nirvana.application.security.Roles;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"email.enabled=true","email.fromAddress=qa@example.test","cors.allowed-origins=https://client.example.test"})
@AutoConfigureMockMvc
@Transactional
class BrowserSignInTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @MockitoBean EmailSender sender;

    @Test void ownerPasswordAndVerifiedSecondFactorCreateARotatingBrowserSession() throws Exception {
        String email = "session-"+UUID.randomUUID()+"@example.test";
        String password = UUID.randomUUID().toString();
        users.saveAndFlush(User.builder().role(roles.findByRoleType(RoleType.Spa_MANAGER)).roles(Set.of(Roles.SPA_OWNER))
                .username(email).email(email).emailVerified(true).active(true).isActive(true)
                .password(passwords.encode(password)).build());
        var challenge = mvc.perform(post("/api/auth/login").header(BrowserSession.HEADER,"browser")
                .contentType("application/json").content(json.writeValueAsBytes(java.util.Map.of("username",email,"password",password))))
                .andReturn().getResponse();
        assertEquals(200,challenge.getStatus());
        var first = json.readTree(challenge.getContentAsString());
        assertTrue(first.get("mfaRequired").asBoolean()); assertTrue(first.path("accessToken").isNull());
        assertNull(challenge.getHeader("Set-Cookie"));
        var body = ArgumentCaptor.forClass(String.class);
        verify(sender).sendOtpEmail(eq(email),anyString(),body.capture());
        var code = Pattern.compile("OTP is ([0-9]{6})").matcher(body.getValue());
        assertTrue(code.find());
        byte[] proof=json.writeValueAsBytes(java.util.Map.of("verificationId",first.get("mfaVerificationId").asText(),"otp",code.group(1)));
        var issued=mvc.perform(post("/api/auth/login/mfa/verify").header(BrowserSession.HEADER,"browser")
                .contentType("application/json").content(proof)).andReturn().getResponse();
        assertEquals(200,issued.getStatus());
        assertTrue(json.readTree(issued.getContentAsString()).path("refreshToken").isNull());
        var access=json.readTree(issued.getContentAsString()).get("accessToken").asText();
        assertEquals(200,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+access)).andReturn().getResponse().getStatus());
        assertNotEquals(200,mvc.perform(post("/api/auth/login/mfa/verify").header(BrowserSession.HEADER,"browser")
                .contentType("application/json").content(proof)).andReturn().getResponse().getStatus());
        Cookie cookie=cookieFrom(issued.getHeader("Set-Cookie"));
        assertNotEquals(200,mvc.perform(post(BrowserSession.PATH).cookie(cookie)).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post(BrowserSession.PATH).header(BrowserSession.HEADER,"browser")
                .header("Origin","https://untrusted.example.test").cookie(cookie)).andReturn().getResponse().getStatus());
        var renewed=mvc.perform(post(BrowserSession.PATH).cookie(cookie).header(BrowserSession.HEADER,"browser")).andReturn().getResponse();
        assertEquals(200,renewed.getStatus()); Cookie rotated=cookieFrom(renewed.getHeader("Set-Cookie"));
        assertNotEquals(cookie.getValue(),rotated.getValue());
        assertEquals(401,mvc.perform(post(BrowserSession.PATH).cookie(cookie).header(BrowserSession.HEADER,"browser")).andReturn().getResponse().getStatus());
        // Logout from a tab that still has the pre-rotation cookie must end the active session.
        var logout=mvc.perform(delete(BrowserSession.PATH).cookie(cookie).header(BrowserSession.HEADER,"browser")).andReturn().getResponse();
        assertEquals(401,mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+access)).andReturn().getResponse().getStatus());
        assertEquals(204,logout.getStatus()); assertTrue(logout.getHeader("Set-Cookie").contains("Max-Age=0"));
        assertEquals(401,mvc.perform(post(BrowserSession.PATH).cookie(rotated).header(BrowserSession.HEADER,"browser")).andReturn().getResponse().getStatus());
    }

    @Test void unverifiedStaffContactRequiresAssistedEnrollment() throws Exception {
        String email = "unenrolled-"+UUID.randomUUID()+"@example.test";
        String password = UUID.randomUUID().toString();
        users.saveAndFlush(User.builder().username(email).email(email).emailVerified(false)
                .role(roles.findByRoleType(RoleType.Spa_MANAGER)).roles(Set.of(Roles.SPA_OWNER))
                .active(true).isActive(true).password(passwords.encode(password)).build());
        var response = mvc.perform(post("/api/auth/login").header(BrowserSession.HEADER,"browser")
                .contentType("application/json").content(json.writeValueAsBytes(java.util.Map.of("username",email,"password",password))))
                .andReturn().getResponse();
        assertEquals(409,response.getStatus());
        assertNull(response.getHeader("Set-Cookie"));
        assertFalse(json.readTree(response.getContentAsString()).hasNonNull("accessToken"));
        verifyNoInteractions(sender);
    }

    private Cookie cookieFrom(String header) {
        assertNotNull(header);
        assertTrue(header.contains("HttpOnly") && header.contains("Secure") && header.contains("SameSite=Strict"));
        String pair=header.split(";",2)[0];
        assertTrue(pair.startsWith(BrowserSession.COOKIE+"="));
        return new Cookie(BrowserSession.COOKIE, pair.substring(pair.indexOf('=')+1));
    }
}
