package com.n11.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.n11.auth.service.FirebaseTokenVerifier.VerifiedPhoneIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebaseTokenVerifierTest {

    @Mock FirebaseAuth firebaseAuth;
    @InjectMocks FirebaseTokenVerifier verifier;

    @Test
    void verify_returnsIdentity_whenTokenHasPhoneClaim() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid-1");
        when(token.getClaims()).thenReturn(Map.of("phone_number", "+905551234567"));
        when(firebaseAuth.verifyIdToken(eq("good-token"), eq(true))).thenReturn(token);

        VerifiedPhoneIdentity identity = verifier.verify("good-token");

        assertThat(identity.firebaseUid()).isEqualTo("firebase-uid-1");
        assertThat(identity.phoneNumber()).isEqualTo("+905551234567");
    }

    @Test
    void verify_throws_whenTokenIsBlank() {
        assertThatThrownBy(() -> verifier.verify(""))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing");
        assertThatThrownBy(() -> verifier.verify(null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verify_throws_whenFirebaseRejectsToken() throws Exception {
        // Simulate Firebase rejecting an expired or tampered ID token. The
        // raw FirebaseAuthException carries Google's reason; we surface a
        // generic BadCredentialsException so we don't leak internals to the
        // client.
        FirebaseAuthException fae = mock(FirebaseAuthException.class);
        when(fae.getMessage()).thenReturn("Token expired");
        when(firebaseAuth.verifyIdToken(eq("bad-token"), eq(true))).thenThrow(fae);

        assertThatThrownBy(() -> verifier.verify("bad-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid Firebase ID token");
    }

    @Test
    void verify_throws_whenPhoneClaimMissing() throws Exception {
        // Firebase ID tokens for email-link login carry no phone_number; we
        // can't impersonate one as a phone signup, so reject explicitly.
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getClaims()).thenReturn(Map.of("email", "u@x.com"));
        when(firebaseAuth.verifyIdToken(eq("emailish"), eq(true))).thenReturn(token);

        assertThatThrownBy(() -> verifier.verify("emailish"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("phone_number");
    }

    @Test
    void verify_throws_whenPhoneClaimIsBlank() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getClaims()).thenReturn(Map.of("phone_number", "   "));
        when(firebaseAuth.verifyIdToken(eq("blank"), eq(true))).thenReturn(token);

        assertThatThrownBy(() -> verifier.verify("blank"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
