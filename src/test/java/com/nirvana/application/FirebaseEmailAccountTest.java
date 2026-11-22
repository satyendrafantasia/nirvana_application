package com.nirvana.application;

import com.nirvana.application.auth.controller.FirebaseEmailLinkController;
import com.nirvana.application.auth.controller.FirebaseEmailLinkController.Proof;
import com.nirvana.application.auth.dto.CompleteRegistrationRequest;
import com.nirvana.application.auth.service.FirebaseEmailLinks;
import com.nirvana.application.auth.service.RegistrationService;
import com.nirvana.application.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class FirebaseEmailAccountTest {
    @Autowired FirebaseEmailLinkController controller;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @MockitoBean FirebaseEmailLinks firebase;

    @Test void registrationAndLaterEmailLinkLoginShareOneAccount() {
        String email = "firebase-link@example.test";
        when(firebase.redeem(eq(email), anyString())).thenReturn(email);
        var form = new CompleteRegistrationRequest();
        form.setRegistrationToken(controller.register(new Proof(email,"register-link")).getRegistrationToken());
        form.setPassword("EmailLinkTest123!");
        form.setFullName("Email Link Customer");
        form.setAcceptPrivacyPolicy(true);
        var registered = registration.completeRegistration(form);
        long before = users.count();
        var session = controller.login(new Proof(email,"login-link"));
        assertEquals(registered.getUser().getId(), session.getUserId());
        assertNotNull(session.getAccessToken());
        assertEquals(before, users.count());
        assertTrue(users.findByEmail(email).orElseThrow().getEmailVerified());
    }

    @Test void loginCannotSilentlyRegisterAnUnknownEmail() {
        when(firebase.redeem("missing@example.test","login-link")).thenReturn("missing@example.test");
        assertThrows(ResponseStatusException.class, () -> controller.login(new Proof("missing@example.test","login-link")));
        assertFalse(users.existsByEmail("missing@example.test"));
    }
}
