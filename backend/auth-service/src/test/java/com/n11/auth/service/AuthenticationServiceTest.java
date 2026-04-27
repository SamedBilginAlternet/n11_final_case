package com.n11.auth.service;

import com.n11.auth.api.dto.AuthTokenResponse;
import com.n11.auth.api.dto.LoginRequest;
import com.n11.auth.api.dto.UserDto;
import com.n11.auth.api.mapper.UserMapper;
import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.repository.UserRepository;
import com.n11.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock UserMapper userMapper;

    @InjectMocks AuthenticationService service;

    @Test
    void issuesJwtForValidCredentials() {
        User user = User.builder()
                .id(7L).email("a@b.com").passwordHash("HASH").fullName("Ada").role(Role.USER).enabled(true).build();
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pwd", "HASH")).thenReturn(true);
        Instant now = Instant.now();
        when(tokenProvider.issue(user)).thenReturn(new JwtTokenProvider.IssuedToken("TOKEN", now, now.plusSeconds(60), 60));
        when(userMapper.toDto(user)).thenReturn(new UserDto(7L, "a@b.com", "Ada", Role.USER, now));

        AuthTokenResponse response = service.login(new LoginRequest("a@b.com", "pwd"));

        assertThat(response.accessToken()).isEqualTo("TOKEN");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(60);
        assertThat(response.user().email()).isEqualTo("a@b.com");
    }

    @Test
    void rejectsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("missing@x.com", "pwd")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsWrongPassword() {
        User user = User.builder()
                .id(7L).email("a@b.com").passwordHash("HASH").fullName("Ada").role(Role.USER).enabled(true).build();
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsDisabledUser() {
        User user = User.builder()
                .id(7L).email("a@b.com").passwordHash("HASH").fullName("Ada").role(Role.USER).enabled(false).build();
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "pwd")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
