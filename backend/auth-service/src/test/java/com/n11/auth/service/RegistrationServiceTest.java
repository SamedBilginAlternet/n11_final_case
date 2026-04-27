package com.n11.auth.service;

import com.n11.auth.api.dto.RegisterRequest;
import com.n11.auth.api.dto.UserDto;
import com.n11.auth.api.mapper.UserMapper;
import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.exception.EmailAlreadyTakenException;
import com.n11.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;

    @InjectMocks RegistrationService service;

    @Test
    void persistsHashedUserAndReturnsDto() {
        RegisterRequest request = new RegisterRequest("Foo@Bar.COM ", "supersecret", " Foo Bar ");
        when(userRepository.existsByEmailIgnoreCase("foo@bar.com")).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            u.setCreatedAt(Instant.now());
            u.setUpdatedAt(Instant.now());
            return u;
        });
        UserDto expected = new UserDto(42L, "foo@bar.com", "Foo Bar", Role.USER, Instant.now());
        when(userMapper.toDto(any(User.class))).thenReturn(expected);

        UserDto actual = service.register(request);

        assertThat(actual).isEqualTo(expected);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("foo@bar.com");
        assertThat(saved.getPasswordHash()).isEqualTo("HASHED");
        assertThat(saved.getFullName()).isEqualTo("Foo Bar");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("dup@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("dup@x.com", "password1", "Dup")))
                .isInstanceOf(EmailAlreadyTakenException.class);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
