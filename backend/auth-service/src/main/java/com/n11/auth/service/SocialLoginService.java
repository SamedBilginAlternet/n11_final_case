package com.n11.auth.service;

import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLoginService {

    private final UserRepository userRepository;

    @Transactional
    public User upsert(String provider, String subject, String email, String fullName) {
        if (provider == null || subject == null || email == null) {
            throw new IllegalArgumentException("provider/subject/email required");
        }
        String normalisedEmail = email.trim().toLowerCase();
        String safeName = (fullName == null || fullName.isBlank()) ? normalisedEmail : fullName.trim();

        return userRepository.findByOauthProviderAndOauthSubject(provider, subject)
                .map(existing -> {
                    log.debug("Social login matched by (provider, subject): userId={}", existing.getId());
                    return existing;
                })
                .or(() -> userRepository.findByEmailIgnoreCase(normalisedEmail).map(existing -> {
                    existing.setOauthProvider(provider);
                    existing.setOauthSubject(subject);
                    User linked = userRepository.save(existing);
                    log.info("Linked existing email {} to provider={} subject={}", normalisedEmail, provider, subject);
                    return linked;
                }))
                .orElseGet(() -> {
                    User created = userRepository.save(User.builder()
                            .email(normalisedEmail)
                            .fullName(safeName)
                            .role(Role.USER)
                            .enabled(true)
                            .oauthProvider(provider)
                            .oauthSubject(subject)
                            .build());
                    log.info("Created new social user id={} email={} provider={}", created.getId(), normalisedEmail, provider);
                    return created;
                });
    }
}
