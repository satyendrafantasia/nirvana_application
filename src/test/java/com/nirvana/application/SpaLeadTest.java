package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class SpaLeadTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private static UsernamePasswordAuthenticationToken as(String role) {
        return new UsernamePasswordAuthenticationToken("t", null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static final String ROWS = """
            [
             {"title":"Lead Test Spa","totalScore":4.9,"reviewsCount":484,
              "address":"1st Floor, Huda Market, Sector 46, Gurugram, Haryana 122003, India",
              "phone":"+91 81784 09586","email":"info@leadtest.example.in","website":"https://www.leadtest.example.in/",
              "socials":{"instagram":"https://www.instagram.com/leadtest","facebook":"javascript:alert(1)"},
              "url":"https://maps.google.com/?cid=18159029113177232331","coordinates":{"lat":28.4358059,"lng":77.0584724}},
             {"title":"Lead Village Spa","address":"Sco 15, Sector 52, Gurugram, Bindapur, Haryana 122003, India",
              "email":"example@mail.com","website":"wa.link/abc","url":"https://maps.google.com/?cid=1136093818730765874"},
             {"title":"Lead Test Spa","url":"https://maps.google.com/?cid=18159029113177232331"},
             {"title":"  ","url":"https://maps.google.com/?cid=1"}
            ]""";

    @Test void onlyAdminsManageLeads() throws Exception {
        for (String role : List.of("ROLE_USER", "ROLE_SPA_OWNER")) {
            mvc.perform(get("/api/admin/spa-leads").with(authentication(as(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/spa-leads/import").with(authentication(as(role))).contentType("application/json").content("[]"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void importsCleansAndKeepsYourProgressOnReimport() throws Exception {
        var admin = as("ROLE_ADMIN");
        mvc.perform(post("/api/admin/spa-leads/import?source=test").with(authentication(admin)).contentType("application/json").content(ROWS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.skipped").value(2));
        var lead = jdbc.queryForMap("SELECT * FROM spa_lead WHERE google_cid='18159029113177232331'");
        assertEquals("Lead Test Spa", lead.get("name"));
        assertEquals("Gurugram", lead.get("city"));
        assertEquals("+91 81784 09586", lead.get("phone"));
        assertEquals(484, ((Number) lead.get("google_review_count")).intValue());
        assertEquals("{\"instagram\":\"https://www.instagram.com/leadtest\"}", lead.get("socials_json"));
        var village = jdbc.queryForMap("SELECT * FROM spa_lead WHERE google_cid='1136093818730765874'");
        assertEquals("Bindapur", village.get("city")); // the address names a village; an import can set the city instead
        assertNull(village.get("email"));   // placeholder address
        assertNull(village.get("website")); // not an http(s) link

        long id = ((Number) lead.get("id")).longValue();
        mvc.perform(patch("/api/admin/spa-leads/" + id).with(authentication(admin)).contentType("application/json")
                .content("{\"status\":\"INTERESTED\",\"notes\":\"Call back Monday\"}")).andExpect(status().isOk());
        assertNotNull(jdbc.queryForObject("SELECT last_contacted_at FROM spa_lead WHERE id=?", Object.class, id));

        mvc.perform(post("/api/admin/spa-leads/import?city=Gurugram").with(authentication(admin)).contentType("application/json")
                        .content(ROWS.replace("+91 81784 09586", "+91 81784 00000")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.updated").value(2));
        var after = jdbc.queryForMap("SELECT * FROM spa_lead WHERE id=?", id);
        assertEquals("+91 81784 00000", after.get("phone"));
        assertEquals("INTERESTED", after.get("status"));
        assertEquals("Call back Monday", after.get("notes"));
        assertEquals("Gurugram", jdbc.queryForObject("SELECT city FROM spa_lead WHERE google_cid='1136093818730765874'", String.class));

        mvc.perform(get("/api/admin/spa-leads?status=INTERESTED&q=Lead").with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead Test Spa"))
                .andExpect(jsonPath("$.counts.INTERESTED").value(1));
        mvc.perform(patch("/api/admin/spa-leads/" + id).with(authentication(admin)).contentType("application/json")
                .content("{\"status\":\"BOGUS\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/spa-leads/import").with(authentication(admin)).contentType("application/json")
                .content("{\"title\":\"not a list\"}")).andExpect(status().isBadRequest());
    }

    @Test void importsGoogleMapsSearchResultsAndMatchesKnownSpas() throws Exception {
        var admin = as("ROLE_ADMIN");
        // A spa first known only by its Maps cid, and one known only by its Place ID.
        mvc.perform(post("/api/admin/spa-leads/import").with(authentication(admin)).contentType("application/json")
                .content("[{\"title\":\"Search Known Spa\",\"email\":\"hi@known.example.in\",\"url\":\"https://maps.google.com/?cid=13831279277064381315\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
        jdbc.update("INSERT INTO spa_lead (google_place_id,name,source,status,imported_at,updated_at) VALUES ('ChIJsearchByPlaceId01','Old name','x','CONTACTED',NOW(),NOW())");
        String search = """
                {"search_parameters":{"q":"spa gurgaon"},"local_results":[
                 {"title":"Search Known Spa","place_id":"ChIJOQ1KGhUZDTkRg-OkojiT8r8","data_cid":"13831279277064381315",
                  "gps_coordinates":{"latitude":28.4785702,"longitude":77.0819966},"rating":4.9,"reviews":310,
                  "address":"Shop No. UG-15, Sector 28, Gurugram, Sarhol, Haryana 122009, India","phone":"+91 99909 95716"},
                 {"title":"Renamed Spa","place_id":"ChIJsearchByPlaceId01","rating":4.6,"reviews":30},
                 {"title":"Search New Spa","place_id":"ChIJsearchNewSpa0001","data_cid":"8623725971384164467","rating":4.6,"reviews":30,
                  "address":"26/35, S City Rd, Sector 41, Gurugram, Haryana 122003, India","website":"https://example.in/x"}
                ]}""";
        mvc.perform(post("/api/admin/spa-leads/import").with(authentication(admin)).contentType("application/json").content(search))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.updated").value(2));
        var known = jdbc.queryForMap("SELECT * FROM spa_lead WHERE google_cid='13831279277064381315'");
        assertEquals("ChIJOQ1KGhUZDTkRg-OkojiT8r8", known.get("google_place_id"));
        assertEquals("hi@known.example.in", known.get("email")); // kept: this export has no email
        assertEquals(310, ((Number) known.get("google_review_count")).intValue());
        var renamed = jdbc.queryForMap("SELECT * FROM spa_lead WHERE google_place_id='ChIJsearchByPlaceId01'");
        assertEquals("Renamed Spa", renamed.get("name"));
        assertEquals("CONTACTED", renamed.get("status"));
        var fresh = jdbc.queryForMap("SELECT * FROM spa_lead WHERE google_cid='8623725971384164467'");
        assertEquals("ChIJsearchNewSpa0001", fresh.get("google_place_id"));
        assertEquals("https://maps.google.com/?cid=8623725971384164467", fresh.get("maps_url"));
        assertEquals("Gurugram", fresh.get("city"));
    }

    /** Run with -Dleads.file=path\to\export.json to check a real export imports cleanly. */
    @Test void importsARealExportWhenOneIsGiven() throws Exception {
        String file = System.getProperty("leads.file");
        Assumptions.assumeTrue(file != null && Files.exists(Path.of(file)));
        var parsed = json.readTree(Files.readString(Path.of(file)));
        int rows = (parsed.has("local_results") ? parsed.get("local_results") : parsed).size();
        mvc.perform(post("/api/admin/spa-leads/import?city=Gurugram&source=real-file-check").with(authentication(as("ROLE_ADMIN")))
                        .contentType("application/json").content(Files.readString(Path.of(file))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(rows)).andExpect(jsonPath("$.skipped").value(0));
        assertEquals(rows, jdbc.queryForObject("SELECT COUNT(*) FROM spa_lead WHERE source='real-file-check'", Integer.class));
    }
}
