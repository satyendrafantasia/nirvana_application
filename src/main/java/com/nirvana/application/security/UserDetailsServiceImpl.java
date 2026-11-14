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

    public UserDetailsServiceImpl(UserRepository userRepository) {
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

    public UserDetails loadUserById(Long id, Set<String> rolesFromToken) {
        return loadUserById(id, rolesFromToken, null);
    }

    /**
     * @param tokenIssuedAt when the presented access token was issued; a token older than the
     *                      account's last password change is refused
     */
    public UserDetails loadUserById(Long id, Set<String> rolesFromToken, java.util.Date tokenIssuedAt) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        if (tokenIssuedAt != null && user.getCredentialsChangedAt() != null) {
            // JWT issue times have whole-second precision, so compare at that precision.
            long changedSecond = user.getCredentialsChangedAt().toEpochSecond();
            if (tokenIssuedAt.toInstant().getEpochSecond() < changedSecond) {
                throw new org.springframework.security.authentication.CredentialsExpiredException("Session ended by a password change");
            }
        }

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
