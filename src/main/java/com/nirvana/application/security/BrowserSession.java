package com.nirvana.application.security;

import com.nirvana.application.auth.dto.RegistrationAuthResponse;
import com.nirvana.application.model.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Duration;

/** Browsers opt in using a non-simple request header; CORS allows only configured origins. */
@RestControllerAdvice
@RequiredArgsConstructor
public class BrowserSession implements ResponseBodyAdvice<Object> {
    public static final String HEADER = "X-Nirvana-Session";
    public static final String OPT_IN = HEADER + "=browser";
    public static final String COOKIE = "nirvana_refresh_session";
    public static final String PATH = "/api/auth/browser-session";
    private final JwtProperties jwt;

    public static String cookie(String value, Duration lifetime) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(true)
                .sameSite("Strict").path(PATH).maxAge(lifetime).build().toString();
    }

    @Override
    public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return true; // Also handles ResponseEntity<AuthResponse> from the one-time OAuth exchange.
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter type, MediaType media,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof AuthResponse) && !(body instanceof RegistrationAuthResponse)) return body;
        response.getHeaders().setCacheControl(CacheControl.noStore());
        if (!"browser".equals(request.getHeaders().getFirst(HEADER))) return body;
        String refresh;
        if (body instanceof AuthResponse auth) {
            refresh = auth.getRefreshToken();
            auth.setRefreshToken(null);
        } else {
            var registration = (RegistrationAuthResponse) body;
            refresh = registration.getRefreshToken();
            registration.setRefreshToken(null);
        }
        if (refresh != null)
            response.getHeaders().add(HttpHeaders.SET_COOKIE, cookie(refresh, Duration.ofDays(jwt.getRefreshExpirationDays())));
        return body;
    }
}
