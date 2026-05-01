package com.n11.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around {@link FirebaseAuth#verifyIdToken} that returns the
 * pieces our phone-login flow actually cares about — the Firebase UID
 * (immutable across phone-number changes) and the verified phone number
 * itself, in E.164 form.
 *
 * Verification is offline once the JWKS cache is warm, so this stays cheap
 * even under bursty traffic.
 */
@Service
@ConditionalOnBean(FirebaseAuth.class)
@RequiredArgsConstructor
@Slf4j
public class FirebaseTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public VerifiedPhoneIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadCredentialsException("Missing Firebase ID token");
        }

        FirebaseToken token;
        try {
            token = firebaseAuth.verifyIdToken(idToken, true);
        } catch (FirebaseAuthException e) {
            log.warn("Firebase ID token rejected: {}", e.getMessage());
            throw new BadCredentialsException("Invalid Firebase ID token");
        }

        Object phoneClaim = token.getClaims().get("phone_number");
        if (!(phoneClaim instanceof String phone) || phone.isBlank()) {
            throw new BadCredentialsException("Token has no phone_number claim");
        }
        return new VerifiedPhoneIdentity(token.getUid(), phone);
    }

    public record VerifiedPhoneIdentity(String firebaseUid, String phoneNumber) {}
}
