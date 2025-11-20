
package com.nirvana.application.security;
import com.nirvana.application.model.User;
import com.nirvana.application.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // If your repository returns Optional<User>, replace this with:
        // User user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(...));
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        List<SimpleGrantedAuthority> authorities = user.getRoles() == null
                ? Collections.emptyList()
                : user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities.toArray(new SimpleGrantedAuthority[0]))
                .accountLocked(!user.isActive())
                .accountExpired(false)
                .credentialsExpired(false)
                .disabled(!user.isActive())
                .build();
    }
}
