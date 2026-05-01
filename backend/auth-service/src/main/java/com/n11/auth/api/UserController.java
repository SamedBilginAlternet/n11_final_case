package com.n11.auth.api;

import com.n11.auth.api.dto.UpdateProfileRequest;
import com.n11.auth.api.dto.UserDto;
import com.n11.auth.api.mapper.UserMapper;
import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.repository.UserRepository;
import com.n11.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Operation(summary = "Return the authenticated user's profile")
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userRepository.findById(principal.userId())
                .map(userMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    @Operation(summary = "Update the authenticated user's profile (only the fields you send)")
    @PatchMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                            @RequestBody @Valid UpdateProfileRequest body) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        if (body.email() != null) {
            String normalised = body.email().trim().toLowerCase();
            if (!normalised.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmailIgnoreCase(normalised)) {
                // Surface the collision so the form can recover; otherwise
                // Postgres throws a 500-level constraint violation.
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu e-posta başka bir hesapta kayıtlı");
            }
            user.setEmail(normalised);
        }
        if (body.fullName() != null && !body.fullName().isBlank()) {
            user.setFullName(body.fullName().trim());
        }

        User saved = userRepository.save(user);
        log.info("User {} updated profile (email={}, fullName set={})",
                saved.getId(), saved.getEmail(), saved.getFullName() != null);
        return userMapper.toDto(saved);
    }

    @Operation(summary = "Admin — list users (newest first)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UserDto> list(@PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return page.map(userMapper::toDto).getContent();
    }

    @Operation(summary = "Admin — promote a user to ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/promote")
    public UserDto promote(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found: " + id));
        u.setRole(Role.ADMIN);
        log.info("Admin {} promoted userId={} to ADMIN", actor.email(), id);
        return userMapper.toDto(userRepository.save(u));
    }

    @Operation(summary = "Admin — demote an ADMIN back to USER (refuses self-demotion)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/demote")
    public UserDto demote(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        if (actor.userId() != null && actor.userId().equals(id)) {
            // If we let an admin demote themselves the next request would 403 and
            // they'd be locked out — refuse client-side instead.
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Kendi rolünü düşüremezsin — başka bir admin'e yaptır.");
        }
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found: " + id));
        u.setRole(Role.USER);
        log.info("Admin {} demoted userId={} to USER", actor.email(), id);
        return userMapper.toDto(userRepository.save(u));
    }
}
