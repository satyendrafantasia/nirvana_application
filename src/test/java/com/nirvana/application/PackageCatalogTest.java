package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The package catalog stays small: counts of spas, a per-spa filter, and a server-side spa search. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class PackageCatalogTest {
    private static final String PUBLISHED = "s.is_active=1 AND s.is_verified=1";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    long packageId;
    long spaId;

    /** Works on a near-empty database too: one active package, taken at one published spa. */
    @BeforeEach void anActivePackageAtAPublishedSpa() {
        packageId = jdbc.queryForObject("SELECT MIN(id) FROM packages", Long.class);
        jdbc.update("UPDATE packages SET is_active=1 WHERE id=?", packageId);
        spaId = jdbc.queryForObject("SELECT MIN(id) FROM spa s WHERE " + PUBLISHED, Long.class);
        jdbc.update("INSERT IGNORE INTO service_package_spa (package_id, spa_id) VALUES (?,?)", packageId, spaId);
        jdbc.update("UPDATE spa SET name='Zephyr Lotus Retreat', city='Lucknow' WHERE id=?", spaId);
    }

    private JsonNode get(String url) throws Exception {
        return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode search(String q) throws Exception {
        return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/packages/spas").param("q", q))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long published(long pkg) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM service_package_spa ps JOIN spa s ON s.id=ps.spa_id WHERE ps.package_id=? AND " + PUBLISHED,
                Long.class, pkg);
    }

    @Test void theCatalogCountsPublishedSpasInsteadOfListingThem() throws Exception {
        JsonNode catalog = get("/api/packages/catalog");
        assertTrue(catalog.size() > 0);
        for (JsonNode p : catalog) {
            assertFalse(p.has("eligibleSpas"), "the spa list is no longer sent");
            assertEquals(published(p.get("id").asLong()), p.get("eligibleSpaCount").asLong());
        }
    }

    @Test void oneSpasPackagesOnly() throws Exception {
        Set<Long> expected = new HashSet<>(jdbc.queryForList("SELECT ps.package_id FROM service_package_spa ps JOIN packages p ON p.id=ps.package_id "
                + "WHERE ps.spa_id=? AND p.is_active=1", Long.class, spaId));
        Set<Long> got = new HashSet<>();
        for (JsonNode p : get("/api/packages/catalog?spaId=" + spaId)) got.add(p.get("id").asLong());
        assertEquals(expected, got);
        assertTrue(got.contains(packageId));
    }

    @Test void spasAreFoundByNameAndCityWithTheirPackages() throws Exception {
        JsonNode found = search("zephyr lucknow");
        assertEquals(1, found.get("total").asLong());
        JsonNode spa = found.get("spas").get(0);
        assertEquals(spaId, spa.get("id").asLong());
        assertEquals("Lucknow", spa.get("city").asText());
        assertTrue(spa.get("packageIds").toString().contains(String.valueOf(packageId)));

        assertEquals(0, search("zephyr pune").get("total").asLong());
        assertEquals(published(packageId), get("/api/packages/spas?packageId=" + packageId + "&limit=1").get("total").asLong());
        assertEquals(1, get("/api/packages/spas?packageId=" + packageId + "&limit=1").get("spas").size());
    }

    @Test void unpublishedSpasNeverShow() throws Exception {
        long before = published(packageId);
        jdbc.update("UPDATE spa SET is_verified=0 WHERE id=?", spaId);
        assertEquals(0, get("/api/packages/spas?q=zephyr").get("total").asLong());
        for (JsonNode p : get("/api/packages/catalog"))
            if (p.get("id").asLong() == packageId) assertEquals(before - 1, p.get("eligibleSpaCount").asLong());
        assertEquals(0, get("/api/packages/catalog?spaId=" + spaId).size());
    }

    @Autowired com.nirvana.application.service.PackageSubscriptionService subscriptions;

    @Test void aPackageCanBeUsedOnlyAtItsSpas() {
        long user = jdbc.queryForObject("SELECT MIN(id) FROM app_user", Long.class);
        jdbc.update("UPDATE user_package_subscription SET status='EXPIRED' WHERE user_id=? AND status='ACTIVE'", user);
        jdbc.update("""
                INSERT INTO user_package_subscription (user_id, package_id, package_type, purchase_date, remaining_sessions, status, payment_status)
                SELECT ?, id, package_type, UTC_TIMESTAMP(6), 3, 'ACTIVE', 'COMPLETED' FROM packages WHERE id=?""", user, packageId);
        long elsewhere = jdbc.queryForObject("SELECT MAX(id) + 1000 FROM spa", Long.class); // a spa the package doesn't cover
        assertTrue(subscriptions.canUsePackage(user, spaId).isCanUse());
        assertFalse(subscriptions.canUsePackage(user, elsewhere).isSpaEligible());
    }

    @Test void browsersMayReusePublicPackageDataBriefly() throws Exception {
        for (String url : new String[]{"/api/packages/catalog", "/api/packages/spas?packageId=" + packageId})
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")));
    }

    @Test void guestsCanSearchWithoutSigningIn() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/packages/spas").param("q", "spa"))
                .andExpect(status().isOk());
    }
}
