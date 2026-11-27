package com.nirvana.application.seo;

import com.nirvana.application.model.dto.ServiceSummaryResponse;
import com.nirvana.application.model.dto.SpaDetailResponse;
import com.nirvana.application.service.impl.SpaReadService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Search-engine views of public pages. Only published spas (active and verified) appear, and only
 * through the same public read model guests already get from /api/spas/{id}.
 */
@Service
public class SeoPages {
    /** Published means listed for guests; the same rule as SpaReadService's public view. */
    static final String PUBLISHED = "s.is_active=1 AND s.is_verified=1";
    private static final int LISTING_LIMIT = 100;

    private final JdbcTemplate jdbc;
    private final SpaReadService spas;
    private final PublicOrigin origin;

    public SeoPages(JdbcTemplate jdbc, SpaReadService spas, PublicOrigin origin) {
        this.jdbc = jdbc;
        this.spas = spas;
        this.origin = origin;
    }

    public static String spaPath(long id) {
        return "/spas/" + id;
    }

    public static String cityPath(String city) {
        return "/spas?city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
    }

    /** The spa's page, or empty when there is no published spa with this id. */
    public Optional<SeoMarkup.Page> spa(long id) {
        SpaDetailResponse spa;
        try {
            spa = spas.getPublicSpaDetails(id);
        } catch (EntityNotFoundException e) {
            return Optional.empty();
        }
        List<ServiceSummaryResponse> menu = spas.getPublicSpaServices(id).stream()
                .filter(s -> !Boolean.FALSE.equals(s.isActive()) && !Boolean.FALSE.equals(s.isVisibleOnMarketplace()))
                .toList();
        Map<String, Object> rating = jdbc.queryForMap(
                "SELECT COUNT(*) AS n, AVG(rating) AS avg FROM review WHERE spa_id=? AND is_visible=1", id);
        long reviews = ((Number) rating.get("n")).longValue();

        String city = blankToNull(spa.city());
        String place = city == null ? "" : " in " + city;
        String title = spa.name() + place + " | Nirvana";
        String description = SeoMarkup.summary(blankToNull(spa.description()) != null ? spa.description()
                : "Book " + spa.name() + place + " on Nirvana. Compare treatments and prices and choose an available appointment time.", 160);
        String priceRange = priceRange(menu, spa.defaultCurrency());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@context", "https://schema.org");
        data.put("@type", "DaySpa");
        String base = origin.get();
        if (!base.isEmpty()) {
            data.put("@id", base + spaPath(id) + "#spa");
            data.put("url", base + spaPath(id));
        }
        data.put("name", spa.name());
        if (!menu.isEmpty()) {
            List<Map<String, Object>> offers = new ArrayList<>();
            for (ServiceSummaryResponse service : menu) {
                Map<String, Object> offer = new LinkedHashMap<>();
                offer.put("@type", "Offer");
                offer.put("itemOffered", Map.of("@type", "Service", "name", service.name()));
                Integer cents = service.priceCents() != null ? service.priceCents() : service.basePriceCents();
                if (cents != null && cents >= 0 && service.currency() != null) {
                    offer.put("price", BigDecimal.valueOf(cents, 2));
                    offer.put("priceCurrency", service.currency());
                }
                offers.add(offer);
            }
            data.put("hasOfferCatalog", Map.of("@type", "OfferCatalog", "name", "Treatments", "itemListElement", offers));
        }
        data.put("description", description);
        List<String> images = (spa.images() == null ? List.<String>of() : spa.images()).stream()
                .filter(url -> url != null && url.startsWith("https://")).limit(5).toList();
        if (!images.isEmpty()) data.put("image", images);
        if (blankToNull(spa.phone()) != null) data.put("telephone", spa.phone());
        Map<String, Object> address = address(spa);
        if (!address.isEmpty()) data.put("address", address);
        if (spa.latitude() != null && spa.longitude() != null)
            data.put("geo", ordered("@type", "GeoCoordinates", "latitude", spa.latitude(), "longitude", spa.longitude()));
        Map<String, Object> hours = openingHours(spa);
        if (hours != null) data.put("openingHoursSpecification", List.of(hours));
        if (priceRange != null) data.put("priceRange", priceRange);
        if (spa.defaultCurrency() != null) data.put("currenciesAccepted", spa.defaultCurrency());
        if (reviews > 0) {
            BigDecimal avg = new BigDecimal(rating.get("avg").toString()).setScale(1, RoundingMode.HALF_UP);
            data.put("aggregateRating", ordered("@type", "AggregateRating", "ratingValue", avg,
                    "reviewCount", reviews, "bestRating", 5, "worstRating", 1));
        }

        StringBuilder body = new StringBuilder("<main><h1>").append(SeoMarkup.text(spa.name())).append("</h1>");
        body.append("<p>").append(SeoMarkup.text(description)).append("</p>");
        String street = address.isEmpty() ? null : (String) address.get("streetAddress");
        String where = joinNonBlank(", ", street, spa.locality(), city, spa.state(), spa.postalCode());
        if (!where.isEmpty()) body.append("<p>").append(SeoMarkup.text(where)).append("</p>");
        if (priceRange != null) body.append("<p>Treatments ").append(SeoMarkup.text(priceRange)).append("</p>");
        if (!menu.isEmpty()) {
            body.append("<ul>");
            menu.stream().limit(20).forEach(s -> body.append("<li>").append(SeoMarkup.text(s.name())).append("</li>"));
            body.append("</ul>");
        }
        body.append("<a href=\"").append(city == null ? "/spas" : SeoMarkup.attr(cityPath(city)))
                .append("\">More spas").append(SeoMarkup.text(place)).append("</a></main>");

        return Optional.of(new SeoMarkup.Page(title, description, spaPath(id), true,
                images.isEmpty() ? spa.heroImageUrl() : images.get(0), "business.business", List.of(data), body.toString()));
    }

    /**
     * The spa listing. Only the plain list and a city list are indexable; other filter combinations
     * are for people and stay out of the index.
     */
    public SeoMarkup.Page listing(String requestedCity, boolean otherFilters) {
        String cityFilter = blankToNull(requestedCity);
        List<Map<String, Object>> rows = cityFilter == null
                ? jdbc.queryForList("SELECT s.id, s.name, s.city FROM spa s WHERE " + PUBLISHED
                        + " ORDER BY s.is_featured DESC, s.rating_avg DESC, s.id LIMIT " + LISTING_LIMIT)
                : jdbc.queryForList("SELECT s.id, s.name, s.city FROM spa s WHERE " + PUBLISHED
                        + " AND s.city=? ORDER BY s.is_featured DESC, s.rating_avg DESC, s.id LIMIT " + LISTING_LIMIT, cityFilter.trim());
        // Use the city as the spas spell it, so /spas?city=pune and ?city=Pune share one canonical URL.
        String city = cityFilter == null ? null : rows.isEmpty() ? cityFilter.trim() : (String) rows.get(0).get("city");
        String title = city == null ? "Spas and wellness centres | Nirvana" : "Spas in " + city + " | Nirvana";
        String description = city == null
                ? "Browse spas across India, compare treatments and prices and book an available appointment time with Nirvana."
                : "Compare spas in " + city + ", their treatments, prices and ratings, and book an available appointment time with Nirvana.";
        String path = city == null ? "/spas" : cityPath(city);
        boolean indexable = !otherFilters && !rows.isEmpty();

        List<Map<String, Object>> items = new ArrayList<>();
        StringBuilder body = new StringBuilder("<main><h1>").append(SeoMarkup.text(city == null ? "Spas and wellness centres" : "Spas in " + city)).append("</h1>");
        body.append("<p>").append(SeoMarkup.text(description)).append("</p><ul>");
        String base = origin.get();
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String name = (String) row.get("name");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("@type", "ListItem");
            item.put("position", items.size() + 1);
            item.put("name", name);
            if (!base.isEmpty()) item.put("url", base + spaPath(id));
            items.add(item);
            body.append("<li><a href=\"").append(spaPath(id)).append("\">").append(SeoMarkup.text(name)).append("</a></li>");
        }
        body.append("</ul></main>");
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("@context", "https://schema.org");
        list.put("@type", "ItemList");
        list.put("name", city == null ? "Spas on Nirvana" : "Spas in " + city);
        list.put("numberOfItems", items.size());
        list.put("itemListElement", items);
        return new SeoMarkup.Page(title, description, path, indexable, null, "website", List.of(list), body.toString());
    }

    public SeoMarkup.Page notFound() {
        return new SeoMarkup.Page("Spa not found | Nirvana", "This spa is not listed on Nirvana.", null, false,
                null, "website", List.of(), "<main><h1>Spa not found</h1><a href=\"/spas\">Browse spas</a></main>");
    }

    public String robots() {
        String base = origin.get();
        if (base.isEmpty()) return "User-agent: *\nDisallow: /\n";
        return "User-agent: *\nAllow: /\n"
                + "Disallow: /api/\nDisallow: /owner\nDisallow: /admin\nDisallow: /me\nDisallow: /booking\nDisallow: /auth\n"
                + "Disallow: /staff\nDisallow: /b/\n"
                + "Sitemap: " + base + "/sitemap.xml\n";
    }

    public String sitemap() {
        String base = origin.get();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        if (!base.isEmpty()) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT s.id, s.city, s.updated_at FROM spa s WHERE " + PUBLISHED + " ORDER BY s.id");
            LocalDate newest = rows.stream().map(r -> day(r.get("updated_at"))).filter(Objects::nonNull)
                    .max(LocalDate::compareTo).orElse(null);
            url(xml, base + "/", newest);
            url(xml, base + "/spas", newest);
            for (String path : List.of("/about", "/contact", "/packages", "/privacy", "/terms")) {
                url(xml, base + path, null);
            }
            Map<String, LocalDate> cities = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Map<String, Object> row : rows) {
                String city = blankToNull((String) row.get("city"));
                if (city == null) continue;
                LocalDate day = day(row.get("updated_at"));
                cities.merge(city.trim(), day == null ? LocalDate.MIN : day, (a, b) -> a.isAfter(b) ? a : b);
            }
            cities.forEach((city, day) -> url(xml, base + cityPath(city), day == LocalDate.MIN ? null : day));
            for (Map<String, Object> row : rows)
                url(xml, base + spaPath(((Number) row.get("id")).longValue()), day(row.get("updated_at")));
        }
        return xml.append("</urlset>\n").toString();
    }

    private static void url(StringBuilder xml, String loc, LocalDate lastmod) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(loc, "UTF-8")).append("</loc>");
        if (lastmod != null) xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        xml.append("</url>\n");
    }

    private static LocalDate day(Object value) {
        if (value instanceof Timestamp t) return t.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        if (value instanceof java.time.LocalDateTime t) return t.toLocalDate();
        if (value instanceof java.time.OffsetDateTime t) return t.toLocalDate();
        return null;
    }

    private static Map<String, Object> address(SpaDetailResponse spa) {
        Map<String, Object> address = new LinkedHashMap<>();
        String street = joinNonBlank(", ", spa.addressLine1(), spa.addressLine2());
        if (!street.isEmpty()) address.put("streetAddress", street);
        if (blankToNull(spa.city()) != null) address.put("addressLocality", spa.city());
        if (blankToNull(spa.state()) != null) address.put("addressRegion", spa.state());
        if (blankToNull(spa.postalCode()) != null) address.put("postalCode", spa.postalCode());
        if (address.isEmpty()) return address;
        address.put("addressCountry", blankToNull(spa.countryCode()) != null ? spa.countryCode().toUpperCase(Locale.ROOT) : "IN");
        Map<String, Object> typed = new LinkedHashMap<>();
        typed.put("@type", "PostalAddress");
        typed.putAll(address);
        return typed;
    }

    private static Map<String, Object> openingHours(SpaDetailResponse spa) {
        String opens = hhmm(spa.openTimeLocal());
        String closes = hhmm(spa.closeTimeLocal());
        if (opens == null || closes == null || spa.workingDays() == null || spa.workingDays().isEmpty()) return null;
        Map<String, Object> hours = new LinkedHashMap<>();
        hours.put("@type", "OpeningHoursSpecification");
        hours.put("dayOfWeek", spa.workingDays().stream().sorted().map(SeoPages::schemaDay).toList());
        hours.put("opens", opens);
        hours.put("closes", closes);
        return hours;
    }

    private static String schemaDay(DayOfWeek day) {
        String name = day.name();
        return "https://schema.org/" + name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String hhmm(String time) {
        return time != null && time.matches("\\d{2}:\\d{2}.*") ? time.substring(0, 5) : null;
    }

    /** "₹1,500–₹4,000" from the visible menu, in major units. */
    static String priceRange(List<ServiceSummaryResponse> menu, String defaultCurrency) {
        List<Integer> prices = menu.stream().map(s -> s.priceCents() != null ? s.priceCents() : s.basePriceCents())
                .filter(p -> p != null && p > 0).sorted().toList();
        if (prices.isEmpty()) return null;
        String currency = menu.stream().map(ServiceSummaryResponse::currency).filter(Objects::nonNull).findFirst()
                .orElse(defaultCurrency == null ? "INR" : defaultCurrency);
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"));
        String symbol = "INR".equalsIgnoreCase(currency) ? "₹" : currency.toUpperCase(Locale.ROOT) + " ";
        String low = symbol + format.format(Math.round(prices.get(0) / 100.0));
        String high = symbol + format.format(Math.round(prices.get(prices.size() - 1) / 100.0));
        return low.equals(high) ? low : low + "–" + high;
    }

    private static String joinNonBlank(String separator, String... parts) {
        return java.util.Arrays.stream(parts).map(SeoPages::blankToNull).filter(Objects::nonNull).map(String::trim)
                .collect(Collectors.joining(separator));
    }

    /** Keys in the order given, so the markup reads naturally ("@type" first). */
    private static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
