package com.nirvana.application.service.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Encrypts guest health answers with AES-256-GCM before they are stored. The key comes from the
 * HEALTH_FORM_KEY environment variable (base64 of 32 random bytes). Without a valid key the feature is
 * off: nothing is stored, and callers are told why. Each ciphertext is bound to its booking (as GCM
 * associated data), so a stored form cannot be copied onto another booking and still decrypt.
 */
@Component
public class HealthFormCipher {
    public static final String UNAVAILABLE =
        "Health forms are switched off: the server has no encryption key (HEALTH_FORM_KEY) configured";
    private static final int IV_BYTES = 12, TAG_BITS = 128;
    private final SecretKeySpec key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public HealthFormCipher(@Value("${app.health-forms.encryption-key:}") String encoded) {
        byte[] raw = decode(encoded);
        this.key = raw == null ? null : new SecretKeySpec(raw, "AES");
        this.keyId = raw == null ? null : fingerprint(raw);
    }

    private static byte[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded.trim());
            return raw.length == 32 ? raw : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A short, non-secret label for the key, so forms written under an older key can be recognised. */
    private static String fingerprint(byte[] raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean enabled() { return key != null; }

    public String keyId() { require(); return keyId; }

    private void require() {
        if (key == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    private static byte[] aad(long bookingId) {
        return ("booking-health-form:" + bookingId).getBytes(StandardCharsets.US_ASCII);
    }

    public byte[] encrypt(long bookingId, byte[] plain) {
        require();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(bookingId));
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the health form", e);
        }
    }

    /** The plain answers, or an exception when the key is wrong or the stored bytes were altered. */
    public byte[] decrypt(long bookingId, byte[] stored) throws GeneralSecurityException {
        require();
        if (stored == null || stored.length <= IV_BYTES) throw new GeneralSecurityException("Truncated ciphertext");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
        cipher.updateAAD(aad(bookingId));
        return cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
    }
}
