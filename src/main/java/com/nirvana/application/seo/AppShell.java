package com.nirvana.application.seo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.regex.Pattern;

/**
 * The built browser app's private shell (frontend/dist/app.html). Spa and listing pages are this
 * shell with real metadata in the head and readable content in the root element, which the app
 * replaces once it starts. People get the normal app; crawlers and link previews need no JavaScript.
 */
@Slf4j
@Component
public class AppShell {
    // The build writes its generic tags with data-site-head; they are swapped for the page's own.
    private static final Pattern BUILD_HEAD = Pattern.compile(
            "[ \\t]*<(title|meta|link|script) data-site-head[^>]*>(?:[^<]*</\\1>)?\\r?\\n?");
    private static final String ROOT = "<div id=\"root\"></div>";
    private static final String FALLBACK = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <link rel="icon" href="/nirvana.svg" type="image/svg+xml" />
              </head>
              <body>
                <div id="root"></div>
              </body>
            </html>
            """;

    private final Path file;
    private volatile String cached;
    private volatile FileTime cachedAt;
    private volatile boolean warned;

    public AppShell(@Value("${seo.app-shell:}") String file) {
        this.file = file.isBlank() ? null : Path.of(file);
    }

    public String render(String head, String body) {
        String html = BUILD_HEAD.matcher(template()).replaceAll("");
        int end = html.indexOf("</head>");
        if (end < 0) throw new IllegalStateException("The app shell has no </head>");
        html = html.substring(0, end) + head + "\n" + html.substring(end);
        return html.replace(ROOT, "<div id=\"root\">" + body + "</div>");
    }

    private String template() {
        if (file == null) return fallback("SEO_APP_SHELL is not set");
        try {
            FileTime modified = Files.getLastModifiedTime(file);
            if (cached == null || !modified.equals(cachedAt)) {
                cached = Files.readString(file, StandardCharsets.UTF_8);
                cachedAt = modified;
            }
            return cached;
        } catch (IOException e) {
            return fallback("the app shell " + file + " could not be read");
        }
    }

    private String fallback(String reason) {
        if (!warned) {
            warned = true;
            log.warn("Spa pages are served without the browser app because {}; point SEO_APP_SHELL at frontend/dist/app.html", reason);
        }
        return FALLBACK;
    }
}
