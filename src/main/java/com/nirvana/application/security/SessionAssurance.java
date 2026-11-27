package com.nirvana.application.security;

import com.nirvana.application.model.User;
import java.util.Set;

public final class SessionAssurance {
    private SessionAssurance() {}

    public static boolean requiresMfa(User user) {
        Set<String> roles = user.getRoles();
        return Boolean.TRUE.equals(user.getMfaEnabled()) || (roles != null && roles.stream()
                .anyMatch(role -> !Roles.USER.equals(role) && !"ROLE_CUSTOMER".equals(role)));
    }

    public static void requirePasswordSignIn(User user) {
        if (requiresMfa(user)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Sign in with your password and registered second factor.");
        }
    }
}
