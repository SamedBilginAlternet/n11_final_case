package com.n11.auth.api;

import com.n11.auth.api.dto.UserDto;
import com.n11.auth.api.mapper.UserMapper;
import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.repository.UserRepository;
import com.n11.common.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;

    @InjectMocks UserController controller;

    private final AuthenticatedUser actor = new AuthenticatedUser(1L, "admin@n11.com", "ADMIN");

    private User userFixture(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@n11.com");
        u.setRole(role);
        u.setCreatedAt(Instant.now());
        return u;
    }

    private UserDto dtoFixture(Long id, Role role) {
        return new UserDto(id, "u" + id + "@n11.com", null, "Name", role, Instant.now());
    }

    @Test
    void promoteFlipsRoleToAdmin() {
        User target = userFixture(7L, Role.USER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toDto(target)).thenReturn(dtoFixture(7L, Role.ADMIN));

        UserDto out = controller.promote(7L, actor);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(out.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void promoteMissingUserThrows404() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.promote(404L, actor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void demoteFlipsRoleBackToUser() {
        User target = userFixture(7L, Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toDto(target)).thenReturn(dtoFixture(7L, Role.USER));

        controller.demote(7L, actor);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void demoteSelfThrows409() {
        // actor.userId() == 1L; demoting self should refuse before any DB hit
        assertThatThrownBy(() -> controller.demote(1L, actor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Kendi rolünü düşüremezsin");
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }
}
