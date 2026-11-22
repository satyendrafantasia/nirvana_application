package com.nirvana.application;

import com.nirvana.application.auth.controller.FirebaseGoogleController;
import com.nirvana.application.auth.controller.FirebaseGoogleController.Connect;
import com.nirvana.application.auth.controller.FirebaseGoogleController.SignIn;
import com.nirvana.application.model.User;
import com.nirvana.application.otp.*;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class FirebaseGoogleAccountTest {
    @Autowired FirebaseGoogleController google;
    @Autowired AuthServiceImpl auth;
    @Autowired UserRepository users;
    @Autowired OtpVerificationRepository codes;
    @MockitoBean FirebasePhoneTokens firebase;
    private User customer;

    @BeforeEach void setUp() {
        customer = auth.createGoogleCustomer("phone-customer@example.test");
        customer.setEmail(null);
        customer.setUsername("+919000000001");
        customer.setPhone("+919000000001");
        customer.setPhoneVerified(true);
        users.saveAndFlush(customer);
        var actor = UserPrincipal.fromUser(customer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
        when(firebase.verifiedGoogle("google-token")).thenReturn(
                new FirebasePhoneTokens.GoogleIdentity("test-project", "google-uid", "google-customer@example.test"));
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private String verifiedCode(long id) {
        String proof = UUID.randomUUID().toString();
        var end = OffsetDateTime.now().plusMinutes(5);
        codes.saveAndFlush(OtpVerification.builder().verificationId(UUID.randomUUID().toString())
                .userId(id).purpose(OtpPurpose.LOGIN).loginToken(proof).verified(true)
                .otpCode("123456").expiresAt(end).loginTokenExpiresAt(end).attempts(1).maxAttempts(5)
                .registrationConsumed(false).passwordResetConsumed(false).loginConsumed(false).build());
        return proof;
    }

    @Test void returningPhoneCustomerKeepsOriginalAccount() {
        long count = users.count();
        String proof = verifiedCode(customer.getId());
        google.connect(new Connect("google-token", proof));
        assertTrue(codes.findByLoginToken(proof).orElseThrow().getLoginConsumed());
        var response = google.signIn(new SignIn("google-token", false));
        assertEquals(customer.getId(), response.getUserId());
        assertEquals(customer.getPhone(), response.getUsername());
        assertNotNull(response.getAccessToken());
        assertEquals(count, users.count());
    }

    @Test void cannotConnectWithAnotherCustomersProof() {
        User other = auth.createGoogleCustomer("other@example.test");
        assertThrows(ResponseStatusException.class, () -> google.connect(new Connect("google-token", verifiedCode(other.getId()))));
        assertNull(google.status().email());
    }

    @Test void explicitRegistrationCreatesOneCustomerAndNextSignInReusesIt() {
        long count = users.count();
        var registered = google.signIn(new SignIn("google-token", true));
        assertEquals(registered.getUserId(), google.signIn(new SignIn("google-token", false)).getUserId());
        assertEquals(count + 1, users.count());
    }

    @Test void signInAloneCannotCreateAnUnlinkedAccount() {
        long count = users.count();
        assertThrows(ResponseStatusException.class, () -> google.signIn(new SignIn("google-token", false)));
        assertEquals(count, users.count());
    }
}
