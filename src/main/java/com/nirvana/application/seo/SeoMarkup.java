package com.nirvana.application.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns page facts into head tags and a crawlable body for the built single-page app shell.
 * Every value is escaped here, so callers pass raw spa names, descriptions and addresses.
 */
public final class SeoMarkup {
    /** Marks tags the server wrote, so the browser app knows to keep them on the first page it renders. */
    public static final String MARKER = "data-server-head";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String SOCIAL_IMAGE = "/nirvana-social.png";

    private SeoMarkup() {}

    /** One page's metadata. {@code canonicalPath} is absent for pages that must not be indexed. */
    public record Page(String title, String description, String canonicalPath, boolean indexable,
                       String image, String ogType, List<Map<String, Object>> jsonLd, String bodyHtml) {}

    public static String head(Page page, String origin) {
        boolean index = page.indexable() && !origin.isEmpty();
        List<String> tags = new ArrayList<>();
        tags.add("<title>" + text(page.title()) + "</title>");
        tags.add(meta("name", "robots", index ? "index,follow" : "noindex,follow"));
        tags.add(meta("name", "description", page.description()));
        tags.add(meta("property", "og:site_name", "Nirvana"));
        tags.add(meta("property", "og:type", page.ogType()));
        tags.add(meta("property", "og:title", page.title()));
        tags.add(meta("property", "og:description", page.description()));
        tags.add(meta("name", "twitter:title", page.title()));
        tags.add(meta("name", "twitter:description", page.description()));
        if (!origin.isEmpty()) {
            String image = page.image() != null && page.image().startsWith("https://") ? page.image() : origin + SOCIAL_IMAGE;
            tags.add(meta("property", "og:image", image));
            tags.add(meta("name", "twitter:card", "summary_large_image"));
            tags.add(meta("name", "twitter:image", image));
            if (index && page.canonicalPath() != null) {
                String url = origin + page.canonicalPath();
                tags.add("<link rel=\"canonical\" href=\"" + attr(url) + "\">");
                tags.add(meta("property", "og:url", url));
            }
        }
        for (Map<String, Object> data : page.jsonLd()) tags.add("<script type=\"application/ld+json\">" + jsonLd(data) + "</script>");
        return String.join("\n", tags.stream().map(tag -> tag.replaceFirst("^<(\\w+)", "<$1 " + MARKER)).toList());
    }

    /**
     * JSON for a script element. Jackson already escapes quotes and control characters; escaping
     * {@code < > &} and the JavaScript line separators as well means no value can close the script tag.
     */
    public static String jsonLd(Map<String, Object> data) {
        try {
            return JSON.writeValueAsString(data)
                    .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
                    .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Structured data could not be written", e);
        }
    }

    public static String text(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    /** {@link HtmlUtils#htmlEscape} covers {@code & < > " '}, which is all an attribute value needs. */
    public static String attr(String value) {
        return text(value);
    }

    private static String meta(String key, String name, String content) {
        return "<meta " + key + "=\"" + name + "\" content=\"" + attr(content) + "\">";
    }

    /** A search-result length summary that ends on a word, not mid-word. */
    public static String summary(String value, int max) {
        if (value == null) return "";
        String flat = value.replaceAll("\\s+", " ").trim();
        if (flat.length() <= max) return flat;
        int cut = flat.lastIndexOf(' ', max - 1);
        return flat.substring(0, cut > max / 2 ? cut : max - 1).replaceAll("[\\s,.;:–-]+$", "") + "…";
    }
}
