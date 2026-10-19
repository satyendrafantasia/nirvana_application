package com.nirvana.application.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public OAuth2AuthenticationSuccessHandler(@org.springframework.context.annotation.Lazy AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported authentication type");
            return;
        }

        OAuth2User oauth2User = token.getPrincipal();
        String provider = token.getAuthorizedClientRegistrationId();
        AuthResponse authResponse = authService.socialLogin(provider, oauth2User);

        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute("SPA_OAUTH_RESULT", authResponse);
        request.getSession().setAttribute("SPA_OAUTH_EXPIRES", System.currentTimeMillis() + 60000L);
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(frontendUrl + "/auth/social/callback");
    }
}
