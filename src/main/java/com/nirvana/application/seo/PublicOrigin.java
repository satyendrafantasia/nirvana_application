package com.nirvana.application.seo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * The one public HTTPS origin search engines should index, from PUBLIC_SITE_URL (the backend twin
 * of the frontend's VITE_PUBLIC_SITE_URL). Unset means a preview: nothing is indexable, robots.txt
 * blocks crawling and the sitemap is empty, the same as an unconfigured frontend build.
 */
@Component
public class PublicOrigin {
    private static final Pattern PRIVATE_HOST = Pattern.compile("(^|\\.)(localhost|test|local|internal)$|^[0-9.]+$");
    private final String origin;

    public PublicOrigin(@Value("${seo.public-origin:}") String value) {
        this.origin = parse(value);
    }

    /** The origin without a trailing slash, or "" for a preview deployment. */
    public String get() {
        return origin;
    }

    static String parse(String value) {
        if (value == null || value.isBlank()) return "";
        URI url;
        try {
            url = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String host = url.getHost();
        String path = url.getRawPath();
        if (!"https".equals(url.getScheme()) || host == null || url.getRawUserInfo() != null
                || !(path == null || path.isEmpty() || path.equals("/")) || url.getRawQuery() != null
                || url.getRawFragment() != null || !host.contains(".") || PRIVATE_HOST.matcher(host).find())
            throw invalid();
        return "https://" + host.toLowerCase() + (url.getPort() > 0 && url.getPort() != 443 ? ":" + url.getPort() : "");
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("PUBLIC_SITE_URL must be the approved HTTPS production origin, such as https://nirvana.example.com");
    }
}
