package com.n11.auth.security;

import com.n11.auth.config.JwtProperties;
import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private final JwtProperties props = new JwtProperties("test-only-please-change-32-byte-secret-1234567890", 60, 30, "n11-auth");
    private final JwtTokenProvider provider = new JwtTokenProvider(props);

    @Test
    void roundTripsClaims() {
        User user = User.builder().id(11L).email("c@d.com").fullName("Cleo").role(Role.ADMIN).build();

        var issued = provider.issue(user);
        var parsed = provider.parse(issued.token());

        assertThat(parsed.userId()).isEqualTo(11L);
        assertThat(parsed.email()).isEqualTo("c@d.com");
        assertThat(parsed.role()).isEqualTo("ADMIN");
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        var other = new JwtTokenProvider(new JwtProperties("a-different-secret-also-32-bytes-long-please-pad", 60, 30, "n11-auth"));
        User user = User.builder().id(1L).email("x@y.com").fullName("X").role(Role.USER).build();
        String token = other.issue(user).token();

        assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(JwtException.class);
    }
}
