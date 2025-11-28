package com.nirvana.application.service;

import com.nirvana.application.model.dto.AuthResponse;
import com.nirvana.application.model.dto.LoginRequest;
import com.nirvana.application.model.dto.LogoutRequest;
import com.nirvana.application.model.dto.RefreshTokenRequest;
import com.nirvana.application.model.dto.UserRegistrationDTO;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface AuthService {

    AuthResponse register(UserRegistrationDTO request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);

    AuthResponse socialLogin(String provider, OAuth2User user);
}
