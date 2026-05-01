package com.n11.auth.service;

import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phone-number sibling of {@link SocialLoginService}.  Given an E.164 number
 * already verified by Firebase, find the matching user or create a new
 * minimal record (no email, no password — those come later via the
 * checkout email prompt and the profile screen respectively).
 *
 * Identity here is the phone number itself, not the Firebase UID.  If a user
 * changes their number through Firebase down the line we treat it as a new
 * account; the alternative (storing Firebase UID alongside oauth_provider)
 * is overkill for the portfolio demo and would require a second migration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneLoginService {

    private final UserRepository userRepository;

    @Transactional
    public User upsertByPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber required");
        }
        String normalised = phoneNumber.trim();

        return userRepository.findByPhoneNumber(normalised)
                .map(existing -> {
                    log.debug("Phone login matched existing userId={}", existing.getId());
                    return existing;
                })
                .orElseGet(() -> {
                    User created = userRepository.save(User.builder()
                            .phoneNumber(normalised)
                            .role(Role.USER)
                            .enabled(true)
                            .build());
                    log.info("Created phone-only user id={} phone={}", created.getId(), normalised);
                    return created;
                });
    }
}
