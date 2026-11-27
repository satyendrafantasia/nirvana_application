package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What search engines and link previews get: sitemap, robots and server-rendered spa pages. */
@SpringBootTest(properties = {
        "seo.public-origin=https://nirvana.example.com",
        "inventory.hide-fixtures=false",
        "seo.app-shell=src/test/resources/seo/app-shell.html"})
@AutoConfigureMockMvc @Transactional
class SeoPagesTest {
    private static final String ORIGIN = "https://nirvana.example.com";
    private static final Pattern JSON_LD = Pattern.compile("<script data-server-head type=\"application/ld\\+json\">(.*?)</script>", Pattern.DOTALL);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    private List<Long> published() {
        return jdbc.queryForList("SELECT id FROM spa WHERE is_active=1 AND is_verified=1 ORDER BY id", Long.class);
    }

    private String html(String url, int status) throws Exception {
        return mvc.perform(get(url).accept("text/html")).andExpect(status().is(status))
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<JsonNode> jsonLd(String html) throws Exception {
        List<JsonNode> blocks = new ArrayList<>();
        Matcher m = JSON_LD.matcher(html);
        while (m.find()) blocks.add(json.readTree(m.group(1)));
        return blocks;
    }

    /** A copy of the seeded spa, published, in the given city. */
    private long copySpa(String city) {
        String columns = String.join(",", jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='spa'
                AND COLUMN_NAME NOT IN ('id','google_place_id','city','is_active','is_verified') ORDER BY ORDINAL_POSITION""", String.class));
        jdbc.update("INSERT INTO spa (" + columns + ",city,is_active,is_verified) SELECT " + columns
                + ",?,1,1 FROM spa ORDER BY id LIMIT 1", city);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test void sitemapListsOnlyPublishedSpas() throws Exception {
        long unverified = copySpa("Pune"), inactive = copySpa("Pune"), listed = copySpa("Navi Mumbai");
        jdbc.update("UPDATE spa SET is_verified=0 WHERE id=?", unverified);
        jdbc.update("UPDATE spa SET is_active=0 WHERE id=?", inactive);
        assertThat(published()).contains(listed).doesNotContain(unverified, inactive);

        byte[] body = mvc.perform(get("/sitemap.xml")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andReturn().getResponse().getContentAsByteArray();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document xml = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        assertThat(xml.getDocumentElement().getNamespaceURI()).isEqualTo("http://www.sitemaps.org/schemas/sitemap/0.9");

        Set<String> locs = new HashSet<>();
        NodeList urls = xml.getElementsByTagNameNS("*", "url");
        for (int i = 0; i < urls.getLength(); i++) {
            var url = (org.w3c.dom.Element) urls.item(i);
            locs.add(url.getElementsByTagNameNS("*", "loc").item(0).getTextContent());
            NodeList lastmod = url.getElementsByTagNameNS("*", "lastmod");
            if (lastmod.getLength() > 0) assertThat(lastmod.item(0).getTextContent()).matches("\\d{4}-\\d{2}-\\d{2}");
        }
        assertThat(locs).contains(ORIGIN + "/", ORIGIN + "/spas");
        Set<String> spaLocs = new HashSet<>();
        for (String loc : locs) if (loc.matches(Pattern.quote(ORIGIN) + "/spas/\\d+")) spaLocs.add(loc);
        Set<String> expected = new HashSet<>();
        for (long id : published()) expected.add(ORIGIN + "/spas/" + id);
        assertThat(spaLocs).isEqualTo(expected)
                .doesNotContain(ORIGIN + "/spas/" + unverified, ORIGIN + "/spas/" + inactive);

        for (String city : jdbc.queryForList("SELECT DISTINCT city FROM spa WHERE is_active=1 AND is_verified=1 AND city<>''", String.class))
            assertThat(locs).anyMatch(loc -> loc.equalsIgnoreCase(ORIGIN + "/spas?city=" + URLEncoder.encode(city.trim(), StandardCharsets.UTF_8)));
        assertThat(locs).contains(ORIGIN + "/spas?city=Navi+Mumbai");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM spa WHERE is_active=1 AND is_verified=1 AND city='Pune'", Integer.class) == 0)
            assertThat(locs).doesNotContain(ORIGIN + "/spas?city=Pune");
    }

    @Test void robotsPointsAtTheSitemapAndKeepsPrivateAreasOut() throws Exception {
        String robots = mvc.perform(get("/robots.txt")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andReturn().getResponse().getContentAsString();
        assertThat(robots).contains("Sitemap: " + ORIGIN + "/sitemap.xml");
        for (String path : List.of("/owner", "/admin", "/me", "/booking", "/auth", "/api/"))
            assertThat(robots).contains("Disallow: " + path + "\n");
        assertThat(robots).doesNotContain("Disallow: /\n");
    }

    @Test void spaPageEscapesTheNameAndCarriesValidStructuredData() throws Exception {
        long id = published().get(0);
        String name = "Rose \"Garden\" <script>alert('x')</script> & Spa";
        String description = "Calm & quiet. </script><img src=x onerror=alert(1)> Line break \"quoted\"";
        jdbc.update("UPDATE spa SET name=?, description=?, open_time_local='09:30:00', close_time_local='21:00:00' WHERE id=?", name, description, id);
        jdbc.update("DELETE FROM spa_working_day WHERE spa_id=?", id);
        jdbc.update("INSERT INTO spa_working_day (spa_id, day_of_week) VALUES (?, 'MONDAY'), (?, 'SATURDAY')", id, id);

        String html = html("/spas/" + id, 200);
        // No markup from spa data reaches the page unescaped.
        assertThat(html).doesNotContain("<script>alert", "<img src=x", "onerror=alert(1)>");
        assertThat(html).contains("<title data-server-head>Rose &quot;Garden&quot; &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; Spa");
        assertThat(html).contains("<link data-server-head rel=\"canonical\" href=\"" + ORIGIN + "/spas/" + id + "\">");
        assertThat(html).contains("<meta data-server-head name=\"robots\" content=\"index,follow\">");
        assertThat(html).contains("property=\"og:title\"", "property=\"og:description\"", "property=\"og:image\"", "name=\"twitter:card\"");
        assertThat(html).contains("<h1>Rose &quot;Garden&quot; &lt;script&gt;");
        // The browser app still loads, and the build's generic noindex head is gone.
        assertThat(html).contains("<script type=\"module\" crossorigin src=\"/assets/index-test.js\"></script>");
        assertThat(html).doesNotContain("data-site-head");
        assertThat(html.indexOf("data-server-head")).isLessThan(html.indexOf("</head>"));

        List<JsonNode> blocks = jsonLd(html);
        assertThat(blocks).hasSize(1);
        Matcher raw = JSON_LD.matcher(html);
        assertThat(raw.find()).isTrue();
        assertThat(raw.group(1)).doesNotContain("<", ">", " ");
        JsonNode spa = blocks.get(0);
        assertThat(spa.path("@context").asText()).isEqualTo("https://schema.org");
        assertThat(spa.path("@type").asText()).isEqualTo("DaySpa");
        assertThat(spa.path("name").asText()).isEqualTo(name);
        assertThat(spa.path("url").asText()).isEqualTo(ORIGIN + "/spas/" + id);
        assertThat(spa.path("description").asText()).startsWith("Calm & quiet. </script>");
        JsonNode hours = spa.path("openingHoursSpecification").get(0);
        assertThat(hours.path("opens").asText()).isEqualTo("09:30");
        assertThat(hours.path("closes").asText()).isEqualTo("21:00");
        assertThat(hours.path("dayOfWeek").toString()).isEqualTo("[\"https://schema.org/Monday\",\"https://schema.org/Saturday\"]");

        Map<String, Object> spaRow = jdbc.queryForMap("SELECT city, latitude, longitude FROM spa WHERE id=?", id);
        if (spaRow.get("city") != null && !spaRow.get("city").toString().isBlank()) {
            assertThat(spa.path("address").path("@type").asText()).isEqualTo("PostalAddress");
            assertThat(spa.path("address").path("addressLocality").asText()).isEqualTo(spaRow.get("city"));
        }
        if (spaRow.get("latitude") != null)
            assertThat(spa.path("geo").path("latitude").decimalValue())
                    .isEqualByComparingTo(new BigDecimal(spaRow.get("latitude").toString()));

        Map<String, Object> reviews = jdbc.queryForMap("SELECT COUNT(*) n, AVG(rating) avg FROM review WHERE spa_id=? AND is_visible=1", id);
        long count = ((Number) reviews.get("n")).longValue();
        if (count == 0) assertThat(spa.has("aggregateRating")).isFalse();
        else {
            JsonNode rating = spa.path("aggregateRating");
            assertThat(rating.path("@type").asText()).isEqualTo("AggregateRating");
            assertThat(rating.path("reviewCount").asLong()).isEqualTo(count);
            assertThat(rating.path("ratingValue").decimalValue()).isEqualByComparingTo(
                    new BigDecimal(reviews.get("avg").toString()).setScale(1, RoundingMode.HALF_UP));
            assertThat(rating.path("bestRating").asInt()).isEqualTo(5);
        }
        Integer menu = jdbc.queryForObject("SELECT COUNT(*) FROM service WHERE spa_id=? AND is_active=1 AND is_visible_on_marketplace=1 AND COALESCE(price_cents, base_price_cents)>0", Integer.class, id);
        if (menu != null && menu > 0) assertThat(spa.path("priceRange").asText()).matches("(₹|[A-Z]{3} )[0-9,]+(–(₹|[A-Z]{3} )[0-9,]+)?");
    }

    @Test void unpublishedAndUnknownSpasAreNotIndexed() throws Exception {
        long id = published().get(0);
        jdbc.update("UPDATE spa SET is_verified=0, name='Hidden Draft Spa' WHERE id=?", id);
        for (String url : List.of("/spas/" + id, "/spas/999999999", "/spas/not-a-number")) {
            String html = html(url, 404);
            assertThat(html).contains("content=\"noindex,follow\"").doesNotContain("Hidden Draft Spa", "rel=\"canonical\"");
            assertThat(html).contains("/assets/index-test.js");
        }
    }

    @Test void cityListingHasOneCanonicalUrlAndFilteredViewsAreNotIndexed() throws Exception {
        String city = jdbc.queryForObject("SELECT city FROM spa WHERE is_active=1 AND is_verified=1 AND city<>'' ORDER BY id LIMIT 1", String.class);
        String canonical = ORIGIN + "/spas?city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
        String html = html("/spas?city=" + URLEncoder.encode(city.toLowerCase(), StandardCharsets.UTF_8), 200);
        assertThat(html).contains("rel=\"canonical\" href=\"" + canonical.replace("&", "&amp;") + "\"");
        assertThat(html).contains("<title data-server-head>Spas in ", "content=\"index,follow\"");
        JsonNode list = jsonLd(html).get(0);
        assertThat(list.path("@type").asText()).isEqualTo("ItemList");
        Integer inCity = jdbc.queryForObject("SELECT COUNT(*) FROM spa WHERE is_active=1 AND is_verified=1 AND city=?", Integer.class, city);
        assertThat(list.path("itemListElement").size()).isEqualTo(Math.min(inCity, 100));
        assertThat(list.path("itemListElement").get(0).path("url").asText()).startsWith(ORIGIN + "/spas/");

        assertThat(html("/spas", 200)).contains("rel=\"canonical\" href=\"" + ORIGIN + "/spas\"");
        String filtered = html("/spas?city=" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&serviceName=Massage", 200);
        assertThat(filtered).contains("content=\"noindex,follow\"").doesNotContain("rel=\"canonical\"");
    }
}
