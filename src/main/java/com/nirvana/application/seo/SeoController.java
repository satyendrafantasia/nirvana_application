package com.nirvana.application.seo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/**
 * Public discovery files and server-rendered spa pages. The reverse proxy sends /robots.txt,
 * /sitemap.xml, /spas and /spas/{id} here; everything else in the browser app stays static.
 */
@RestController
public class SeoController {
    private static final MediaType HTML = MediaType.parseMediaType("text/html;charset=UTF-8");
    private final SeoPages pages;
    private final AppShell shell;
    private final PublicOrigin origin;

    public SeoController(SeoPages pages, AppShell shell, PublicOrigin origin) {
        this.pages = pages;
        this.shell = shell;
        this.origin = origin;
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(pages.robots());
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(pages.sitemap());
    }

    @GetMapping("/spas")
    public ResponseEntity<String> listing(@RequestParam(required = false) String city, HttpServletRequest request) {
        boolean otherFilters = request.getParameterMap().keySet().stream().anyMatch(name -> !name.equals("city"));
        return html(HttpStatus.OK, pages.listing(city, otherFilters));
    }

    @GetMapping("/spas/{id}")
    public ResponseEntity<String> spa(@PathVariable String id) {
        Optional<SeoMarkup.Page> page = id.matches("\\d{1,18}") ? pages.spa(Long.parseLong(id)) : Optional.empty();
        return page.map(p -> html(HttpStatus.OK, p)).orElseGet(() -> html(HttpStatus.NOT_FOUND, pages.notFound()));
    }

    private ResponseEntity<String> html(HttpStatus status, SeoMarkup.Page page) {
        // HTML is revalidated on every visit so a renamed or unpublished spa never lingers in a cache.
        return ResponseEntity.status(status).contentType(HTML).cacheControl(CacheControl.noCache())
                .body(shell.render(SeoMarkup.head(page, origin.get()), page.bodyHtml()));
    }
}
