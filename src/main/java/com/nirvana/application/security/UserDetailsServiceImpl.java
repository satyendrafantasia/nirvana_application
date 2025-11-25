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
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));

        // Prefer roles from token to avoid extra join if present, otherwise fall back to DB roles
        Set<GrantedAuthority> authorities = rolesFromToken != null && !rolesFromToken.isEmpty()
                ? rolesFromToken.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet())
                : UserPrincipal.buildAuthorities(user.getRoles());

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
