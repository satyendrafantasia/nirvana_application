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
@EnableConfigurationProperties({JwtProperties.class, PaymentMfaVerifier.MfaProperties.class})
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
                .cors(org.springframework.security.config.Customizer.withDefaults())
                // API credentials are explicit bearers. Cookie exchanges require the custom
                // browser header, enforced at their mappings and subject to the CORS allowlist.
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        (request, response, exception) -> {response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"message\":\"Please sign in\"}");},
                        request -> request.getRequestURI().startsWith("/api/")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/api/auth/**",
                                "/api/payments/razorpay/webhook",
                                "/api/public/google-booking/**",
                                "/api/public/discovery/**",
                                // A spa answers its booking SMS from a private link, without an account.
                                "/api/public/spa-bookings/**",
                                "/api/public/support/**",
                                // Unsubscribe from checkout reminders with the signed link in the email.
                                "/api/public/checkout-reminders/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/management/health",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        // Guests browse without an account: search, published spa pages, their
                        // treatments, reviews, Google photos, packages and FAQs. Booking and buying
                        // still need sign-in.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/spas/search",
                                "/api/spas/{id:\\d+}",
                                "/api/spas/{id:\\d+}/services",
                                "/api/spas/{id:\\d+}/reviews",
                                "/api/spas/{id:\\d+}/google-photos",
                                "/api/spas/{id:\\d+}/google-photos/{photoId}/image",
                                "/api/packages/catalog",
                                "/api/packages/spas",
                                "/api/v1/spa/{id:\\d+}/packages/available",
                                "/api/faqs",
                                // Crawler files and the server-rendered public spa pages.
                                "/robots.txt",
                                "/sitemap.xml",
                                "/spas",
                                "/spas/*"
                        ).permitAll()
                        .requestMatchers("/management/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_PLATFORM_ADMIN")
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
                        "/webjars/**"
                );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Only bcrypt hashes are accepted. Plain-text ({noop}) and every other stored
        // format never match, so a seeded or hand-inserted password cannot sign in.
        var bcrypt = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var encoder = new org.springframework.security.crypto.password.DelegatingPasswordEncoder(
                "bcrypt", Map.of("bcrypt", bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                throw new UnsupportedOperationException("Unsupported password format");
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                // Older installations used bare bcrypt strings. Never accept plain text or
                // arbitrary delegated formats. The first successful login adds the marker.
                return encodedPassword != null && encodedPassword.startsWith("$2") && bcrypt.matches(rawPassword, encodedPassword);
            }
        });
        return encoder;
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
    public OAuth2ClientProperties spaOAuthProperties(org.springframework.core.env.Environment environment) {
        var properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
            .bind("security.oauth2.client", org.springframework.boot.context.properties.bind.Bindable.of(OAuth2ClientProperties.class))
            .orElseGet(OAuth2ClientProperties::new);
        properties.getRegistration().entrySet().removeIf(e -> e.getValue().getClientId() == null || e.getValue().getClientId().isBlank() || e.getValue().getClientSecret() == null || e.getValue().getClientSecret().isBlank());
        return properties;
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

        return registrations.isEmpty() ? id -> null : new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration buildRegistration(String registrationId, OAuth2ClientProperties clientProperties) {
        OAuth2ClientProperties.Registration reg = clientProperties.getRegistration().get(registrationId);
        if (reg == null || reg.getClientId() == null || reg.getClientId().isBlank() || reg.getClientSecret() == null || reg.getClientSecret().isBlank()) return null;

        OAuth2ClientProperties.Provider provider = null;
        String providerId = reg.getProvider() != null ? reg.getProvider() : registrationId;
        provider = clientProperties.getProvider().get(providerId);
        ClientRegistration.Builder base;
        try { base = org.springframework.security.config.oauth2.client.CommonOAuth2Provider.valueOf(providerId.toUpperCase()).getBuilder(registrationId); }
        catch (IllegalArgumentException unknown) { base = ClientRegistration.withRegistrationId(registrationId); }
        ClientRegistration.Builder builder = base
                .clientId(reg.getClientId())
                .clientSecret(reg.getClientSecret())
                .authorizationGrantType(resolveGrantType(reg.getAuthorizationGrantType()))
                .redirectUri(reg.getRedirectUri() != null ? reg.getRedirectUri() : "{baseUrl}/oauth2/callback/{registrationId}");

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
