package com.nirvana.application.auth.controller;

import com.nirvana.application.config.SmsProperties;
import com.nirvana.application.otp.FirebasePhoneTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tells the site how phone codes work here: "FIREBASE" (the browser asks Google to text and check the
 * code), "SMS" (we text it and check what the user types) or "OFF" (no phone codes; use email).
 * Firebase's web settings are public by design; the API key only identifies the project.
 */
@RestController
@RequiredArgsConstructor
public class PhoneCodeConfigController {
    private final SmsProperties sms;
    private final FirebasePhoneTokens firebase;

    @Value("${firebase.web-api-key:}")
    private String apiKey;
    @Value("${firebase.auth-domain:}")
    private String authDomain;
    @Value("${firebase.project-id:}")
    private String projectId;

    @GetMapping({"/api/auth/phone-codes", "/auth/phone-codes"})
    public Map<String, Object> phoneCodes() {
        if (sms.isFirebase())
            return firebase.configured() && !apiKey.isBlank()
                    ? Map.of("mode", "FIREBASE", "firebase", Map.of("apiKey", apiKey,
                            "authDomain", authDomain.isBlank() ? projectId + ".firebaseapp.com" : authDomain, "projectId", projectId))
                    : Map.of("mode", "OFF");
        return Map.of("mode", sms.isEnabled() ? "SMS" : "OFF");
    }
}
