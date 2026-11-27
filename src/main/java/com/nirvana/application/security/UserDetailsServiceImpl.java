package com.nirvana.application.security;

import com.nirvana.application.model.User;
import com.nirvana.application.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    private final com.nirvana.application.repository.RefreshTokenRepository sessions;

    public UserDetailsServiceImpl(UserRepository userRepository, com.nirvana.application.repository.RefreshTokenRepository sessions) {
        this.sessions = sessions;
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return UserPrincipal.fromUser(user);
    }

    public UserDetails loadSessionUser(Long id, java.util.Date tokenIssuedAt, boolean mfaVerified, String family) {
        if (family == null || !sessions.existsByUser_IdAndSessionFamilyAndRevokedAtIsNullAndExpiresAtAfter(
                id, family, java.time.OffsetDateTime.now()))
            throw new org.springframework.security.authentication.CredentialsExpiredException("Session has ended");
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        if (tokenIssuedAt != null && user.getCredentialsChangedAt() != null) {
            // JWT issue times have whole-second precision, so compare at that precision.
            long changedSecond = user.getCredentialsChangedAt().toEpochSecond();
            if (tokenIssuedAt.toInstant().getEpochSecond() < changedSecond) {
                throw new org.springframework.security.authentication.CredentialsExpiredException("Session ended by a password change");
            }
        }

        if (SessionAssurance.requiresMfa(user) && !mfaVerified)
            throw new org.springframework.security.authentication.InsufficientAuthenticationException("Complete multifactor sign-in");
        Set<GrantedAuthority> authorities = UserPrincipal.buildAuthorities(user.getRoles());
        if (!Boolean.TRUE.equals(user.getIsActive())) throw new org.springframework.security.authentication.DisabledException("Account disabled");

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getIsActive()),
                authorities
        );
    }
}
