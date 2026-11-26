package com.nirvana.application.config;

import io.sentry.Breadcrumb;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Keeps personal data out of Sentry. The SDK only runs when SENTRY_DSN is set, and is configured with
 * send-default-pii false and no request bodies; these callbacks additionally drop headers, cookies,
 * query strings and the user, and mask emails, phone numbers, codes and tokens in messages and URLs.
 */
@Configuration
@ConditionalOnClass(SentryOptions.class)
public class SentryScrubbing {
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SECRET_PARAM = Pattern.compile(
            "(?i)\\b(otp|code|token|access_token|refresh_token|password|secret|signature|key)([\"']?\\s*[=:]\\s*[\"']?)[^\\s&\"',;}]+");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])(?:\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}(?![\\w])");
    private static final Pattern LONG_TOKEN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9_-]{24,}(?![A-Za-z0-9])");
    private static final Pattern OTP = Pattern.compile("(?<![\\w.])\\d{6}(?![\\w.])");

    /** Masks personal values in free text such as exception and log messages. */
    public static String scrub(String text) {
        if (text == null) return null;
        String out = BEARER.matcher(text).replaceAll("Bearer [token]");
        out = JWT.matcher(out).replaceAll("[token]");
        out = EMAIL.matcher(out).replaceAll("[email]");
        out = SECRET_PARAM.matcher(out).replaceAll("$1$2[redacted]");
        out = PHONE.matcher(out).replaceAll("[phone]");
        out = LONG_TOKEN.matcher(out).replaceAll("[token]");
        return OTP.matcher(out).replaceAll("[number]");
    }

    /** A URL without its query or fragment, with tokens in the path masked. */
    public static String scrubUrl(String url) {
        if (url == null) return null;
        int cut = url.length();
        for (char c : new char[] {'?', '#'}) {
            int at = url.indexOf(c);
            if (at >= 0 && at < cut) cut = at;
        }
        return scrub(url.substring(0, cut));
    }

    @Bean
    SentryOptions.BeforeSendCallback sentryBeforeSend() {
        return (event, hint) -> {
            event.setUser(null);
            event.setServerName(null);
            Request request = event.getRequest();
            if (request != null) {
                request.setData(null);
                request.setCookies(null);
                request.setHeaders(null);
                request.setQueryString(null);
                request.setEnvs(null);
                request.setUrl(scrubUrl(request.getUrl()));
            }
            Message message = event.getMessage();
            if (message != null) {
                message.setFormatted(scrub(message.getFormatted()));
                message.setMessage(scrub(message.getMessage()));
                message.setParams(null);
            }
            if (event.getExceptions() != null)
                for (SentryException exception : event.getExceptions()) exception.setValue(scrub(exception.getValue()));
            if (event.getBreadcrumbs() != null) event.getBreadcrumbs().forEach(SentryScrubbing::scrubBreadcrumb);
            event.setExtras(null);
            return event;
        };
    }

    @Bean
    SentryOptions.BeforeBreadcrumbCallback sentryBeforeBreadcrumb() {
        return (breadcrumb, hint) -> {
            scrubBreadcrumb(breadcrumb);
            return breadcrumb;
        };
    }

    private static void scrubBreadcrumb(Breadcrumb breadcrumb) {
        breadcrumb.setMessage(scrub(breadcrumb.getMessage()));
        Map<String, Object> data = breadcrumb.getData();
        for (String key : data.keySet().toArray(String[]::new)) {
            Object value = data.get(key);
            if (value instanceof Number || value instanceof Boolean) continue;
            if (!(value instanceof String text)) {
                breadcrumb.removeData(key);
                continue;
            }
            breadcrumb.setData(key, key.toLowerCase().contains("url") ? scrubUrl(text) : scrub(text));
        }
    }
}
