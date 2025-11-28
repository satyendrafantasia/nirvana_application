// java
package com.nirvana.application.config;

import com.nirvana.application.security.JwtAuthenticationFilter;
import com.nirvana.application.security.JwtProperties;
import com.nirvana.application.security.PaymentMfaVerifier;
import com.nirvana.application.security.RateLimitingFilter;
import com.nirvana.application.security.OAuth2AuthenticationFailureHandler;
import com.nirvana.application.security.OAuth2AuthenticationSuccessHandler;
import com.nirvana.application.security.UserDetailsServiceImpl;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, PaymentMfaVerifier.MfaProperties.class, OAuth2ClientProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final RateLimitingFilter rateLimitingFilter;
    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, UserDetailsServiceImpl userDetailsService,
                          RateLimitingFilter rateLimitingFilter,
                          OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler,
                          OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.rateLimitingFilter = rateLimitingFilter;
        this.oauth2AuthenticationSuccessHandler = oauth2AuthenticationSuccessHandler;
        this.oauth2AuthenticationFailureHandler = oauth2AuthenticationFailureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/api/auth/**",
                                "/api/payments/razorpay/webhook",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/management/**",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(userDetailsService)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint.baseUri("/oauth2/authorization"))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/oauth2/callback/*"))
                        .successHandler(oauth2AuthenticationSuccessHandler)
                        .failureHandler(oauth2AuthenticationFailureHandler)
                )
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/management/**"
                );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties clientProperties) {
        Map<String, OAuth2ClientProperties.Registration> regs = clientProperties.getRegistration();
        if (regs == null || regs.isEmpty()) {
            // Return a no-op repository when no registrations are configured to avoid
            // InMemoryClientRegistrationRepository rejecting an empty list.
            return registrationId -> null;
        }

        List<ClientRegistration> registrations = regs.keySet().stream()
                .map(id -> buildRegistration(id, clientProperties))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration buildRegistration(String registrationId, OAuth2ClientProperties clientProperties) {
        OAuth2ClientProperties.Registration reg = clientProperties.getRegistration().get(registrationId);
        if (reg == null) return null;

        OAuth2ClientProperties.Provider provider = null;
        if (reg.getProvider() != null) {
            provider = clientProperties.getProvider().get(reg.getProvider());
        }

        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .clientId(reg.getClientId())
                .clientSecret(reg.getClientSecret())
                .authorizationGrantType(resolveGrantType(reg.getAuthorizationGrantType()))
                .redirectUri(reg.getRedirectUri() != null ? reg.getRedirectUri() : "{baseUrl}/login/oauth2/code/{registrationId}");

        if (reg.getScope() != null && !reg.getScope().isEmpty()) {
            builder.scope(reg.getScope().toArray(new String[0]));
        }

        if (provider != null) {
            if (provider.getAuthorizationUri() != null) builder.authorizationUri(provider.getAuthorizationUri());
            if (provider.getTokenUri() != null) builder.tokenUri(provider.getTokenUri());
            if (provider.getUserInfoUri() != null) builder.userInfoUri(provider.getUserInfoUri());
            if (provider.getJwkSetUri() != null) builder.jwkSetUri(provider.getJwkSetUri());
            if (provider.getUserNameAttribute() != null) builder.userNameAttributeName(provider.getUserNameAttribute());
        }

        return builder.build();
    }

    private AuthorizationGrantType resolveGrantType(String type) {
        if (type == null) return AuthorizationGrantType.AUTHORIZATION_CODE;
        switch (type.toLowerCase()) {
            case "client_credentials":
                return AuthorizationGrantType.CLIENT_CREDENTIALS;
            case "authorization_code":
                return AuthorizationGrantType.AUTHORIZATION_CODE;
            case "refresh_token":
                return AuthorizationGrantType.REFRESH_TOKEN;
            case "implicit":
                return new AuthorizationGrantType("implicit");
            default:
                return new AuthorizationGrantType(type);
        }
    }

}
