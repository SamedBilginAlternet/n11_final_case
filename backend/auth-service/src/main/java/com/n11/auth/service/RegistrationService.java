package com.n11.auth.service;

import com.n11.auth.api.dto.RegisterRequest;
import com.n11.auth.api.dto.UserDto;
import com.n11.auth.api.mapper.UserMapper;
import com.n11.auth.domain.Role;
import com.n11.auth.domain.User;
import com.n11.auth.exception.EmailAlreadyTakenException;
import com.n11.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Transactional
    public UserDto register(RegisterRequest request) {
        String normalised = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalised)) {
            throw new EmailAlreadyTakenException(normalised);
        }

        User user = User.builder()
                .email(normalised)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(Role.USER)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered user id={} email={}", saved.getId(), saved.getEmail());
        return userMapper.toDto(saved);
    }
}
