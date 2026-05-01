package com.n11.auth.service;

import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneLoginServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks PhoneLoginService service;

    @Test
    void upsertByPhone_returnsExisting_whenPhoneAlreadyKnown() {
        User existing = User.builder().id(42L).phoneNumber("+905551234567").build();
        when(userRepository.findByPhoneNumber("+905551234567")).thenReturn(Optional.of(existing));

        User result = service.upsertByPhone("+905551234567");

        assertThat(result).isSameAs(existing);
        // Critical: never persist a new row when the lookup hit. A redundant
        // save would bump updated_at on every login and pollute audit logs.
        verify(userRepository, never()).save(any());
    }

    @Test
    void upsertByPhone_createsNewUser_whenPhoneUnseen() {
        when(userRepository.findByPhoneNumber("+905551234567")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        User result = service.upsertByPhone("+905551234567");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        // Phone is the only identity we know; email/fullName stay null until
        // the user provides them via checkout email gate or onboarding modal.
        assertThat(saved.getPhoneNumber()).isEqualTo("+905551234567");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getFullName()).isNull();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(result.getId()).isEqualTo(99L);
    }

    @Test
    void upsertByPhone_trimsWhitespace_beforeLookup() {
        User existing = User.builder().id(7L).phoneNumber("+905551234567").build();
        when(userRepository.findByPhoneNumber("+905551234567")).thenReturn(Optional.of(existing));

        // Firebase shouldn't hand us padded phone strings, but defending
        // against trailing whitespace cheaply prevents 'same number, two
        // accounts' bugs if input ever sneaks through.
        User result = service.upsertByPhone("  +905551234567  ");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void upsertByPhone_rejectsBlankInput() {
        assertThatThrownBy(() -> service.upsertByPhone(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upsertByPhone(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
