package com.n11.auth.service;

import com.n11.auth.config.JwtProperties;
import com.n11.auth.domain.RefreshToken;
import com.n11.auth.domain.User;
import com.n11.auth.repository.RefreshTokenRepository;
import com.n11.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates, and revokes opaque refresh tokens.
 *
 * <p>Why opaque (random bytes) instead of JWT for refresh: refresh tokens
 * MUST be revocable server-side. Stateful lookup by hash is unavoidable; if
 * we have to hit the DB anyway, encoding claims as JWT is dead weight.</p>
 *
 * <p>Why hashed at rest: a leaked DB dump should not yield usable tokens.
 * SHA-256 (deterministic) is required because we look up by hash; bcrypt's
 * per-row salt would force a full-table scan.</p>
 *
 * <p>Reuse detection: if a token presented at /refresh is found but already
 * revoked, an attacker has stolen and replayed it. We revoke the entire
 * family so they can't keep escalating with the rotated token they grabbed.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 48; // 384 bits → 64-char base64url

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final JwtProperties properties;

    @Transactional
    public Issued issueNewFamily(User user, String userAgent, String ip) {
        return persist(user, UUID.randomUUID(), null, userAgent, ip);
    }

    /**
     * Validates and rotates the presented refresh token. Returns a new token
     * (same family, with replaced_by chain) and the resolved user. The old
     * token is marked revoked atomically inside the same transaction.
     */
    @Transactional
    public RotateResult rotate(String presented, String userAgent, String ip) {
        String hash = sha256(presented);
        RefreshToken stored = repository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        Instant now = Instant.now();

        if (stored.getRevokedAt() != null) {
            // Replay of a token we already retired — treat the whole chain as
            // compromised and force the user to re-authenticate from scratch.
            int affected = repository.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for userId={} familyId={} — revoked {} sibling token(s)",
                    stored.getUserId(), stored.getFamilyId(), affected);
            throw new BadCredentialsException("Refresh token reuse detected");
        }

        if (!stored.getExpiresAt().isAfter(now)) {
            throw new BadCredentialsException("Refresh token expired");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        if (!user.isEnabled()) {
            throw new BadCredentialsException("User disabled");
        }

        Issued next = persist(user, stored.getFamilyId(), null, userAgent, ip);

        stored.setRevokedAt(now);
        stored.setReplacedById(next.entityId());
        repository.save(stored);

        return new RotateResult(user, next);
    }

    @Transactional
    public void revoke(String presented) {
        if (presented == null || presented.isBlank()) return;
        repository.findByTokenHash(sha256(presented))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> repository.revokeById(t.getId(), Instant.now()));
    }

    private Issued persist(User user, UUID familyId, Long replacedById,
                           String userAgent, String ip) {
        String raw = generateRawToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(properties.refreshTtlDays()));

        RefreshToken entity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(raw))
                .familyId(familyId)
                .expiresAt(expiresAt)
                .replacedById(replacedById)
                .userAgent(truncate(userAgent, 255))
                .ip(truncate(ip, 64))
                .build();
        entity = repository.save(entity);

        return new Issued(entity.getId(), raw, expiresAt,
                Duration.between(now, expiresAt).toSeconds(), familyId);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Issued(Long entityId, String rawToken, Instant expiresAt,
                         long expiresInSeconds, UUID familyId) {}

    public record RotateResult(User user, Issued issued) {}
}
