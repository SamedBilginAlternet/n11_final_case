package com.n11.auth.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wires a {@link FirebaseAuth} bean from the service-account JSON pulled out
 * of Infisical.  The bean is only created when the env var is non-blank, so
 * local dev and CI runs without a Firebase project still boot — the phone
 * login endpoint just won't be wired in that case.
 *
 * The service account JSON ships as a single multi-line string; we feed it
 * straight into {@link GoogleCredentials#fromStream} instead of writing it
 * to disk so secrets never touch the filesystem on the droplet.
 */
@Configuration
@ConditionalOnProperty(prefix = "n11.firebase", name = "service-account-json")
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(
            @Value("${n11.firebase.service-account-json}") String serviceAccountJson) throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(
                        new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))))
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("FirebaseApp initialised for projectId={}", app.getOptions().getProjectId());
        return app;
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }
}
