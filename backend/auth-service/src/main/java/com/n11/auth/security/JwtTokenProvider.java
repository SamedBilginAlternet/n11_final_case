package com.n11.auth.security;

import com.n11.auth.config.JwtProperties;
import com.n11.auth.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final JwtProperties props;
    private final SecretKey key;
    private final Duration accessTtl;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.accessTtlMinutes());
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtl);

        String token = Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claims(Map.of(
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "role", user.getRole().name()
                ))
                .signWith(key)
                .compact();

        return new IssuedToken(token, now, expiresAt, accessTtl.toSeconds());
    }

    public ParsedToken parse(String token) {
        var jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token);

        var claims = jws.getPayload();
        return new ParsedToken(
                Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class),
                claims.getExpiration().toInstant()
        );
    }

    public record IssuedToken(String token, Instant issuedAt, Instant expiresAt, long expiresInSeconds) {}

    public record ParsedToken(Long userId, String email, String role, Instant expiresAt) {}
}
