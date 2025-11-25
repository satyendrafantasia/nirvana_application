package com.nirvana.application.service.impl;

import com.nirvana.application.model.Role;
import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.dto.LoginRequest;
import com.nirvana.application.model.dto.UserRegistrationDTO;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.repository.RoleRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.security.JwtTokenService;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.AuthService;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static com.nirvana.application.security.Roles.PLATFORM_ADMIN;
import static com.nirvana.application.security.Roles.SPA_OWNER;
import static com.nirvana.application.security.Roles.USER;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    @Override
    @Transactional
    public AuthResponse register(UserRegistrationDTO request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new EntityExistsException("Username already exists");
        }

        RoleType roleType = Optional.ofNullable(request.getRoleType()).orElse(RoleType.CUSTOMER);
        Role role = Optional.ofNullable(roleRepository.findByRoleType(roleType))
                .orElseThrow(() -> new EntityNotFoundException("Role not configured: " + roleType));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getUsername())
                .name(request.getName())
                .lastName(request.getLastName())
                .role(role)
                .roles(resolveRoleNames(roleType))
                .password(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .isActive(true)
                .timezone("UTC")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + principal.getId()));

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenService.generateToken(user);
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .build();
    }

    private Set<String> resolveRoleNames(RoleType roleType) {
        return switch (roleType) {
            case ADMIN -> Set.of(PLATFORM_ADMIN);
            case Spa_MANAGER -> Set.of(SPA_OWNER);
            case CUSTOMER -> Set.of(USER);
        };
    }
}
