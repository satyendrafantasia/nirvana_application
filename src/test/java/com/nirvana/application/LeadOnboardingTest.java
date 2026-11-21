package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.service.LeadOnboardingService;
import com.nirvana.application.service.SpaLeadService;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class LeadOnboardingTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired SpaLeadService leads;
    @Autowired LeadOnboardingService onboarding;
    @Autowired SlotAvailabilityService availability;
    @Autowired com.nirvana.application.service.impl.SpaSearchService search;

    private static final String SEARCH = """
            {"local_results":[
             {"title":"Onboard Test Spa","place_id":"ChIJonboardTestSpa0001","data_cid":"990000000000000001",
              "address":"Shop 4, Huda Market, Sector 31, Gurugram, Haryana 122001, India","phone":"+91 90000 00001",
              "gps_coordinates":{"latitude":28.45,"longitude":77.05},
              "operating_hours":{"monday":"10 AM–10 PM","tuesday":"10 AM–10 PM","wednesday":"Closed","thursday":"9:30 AM–9:30 PM",
                                 "friday":"Open 24 hours","saturday":"10 AM–10 PM","sunday":"10 AM–10 PM"}},
             {"title":"Onboard No Hours Spa","data_cid":"990000000000000002","address":"Sector 14, Gurugram, Haryana 122007, India"},
             {"title":"Onboard Declined Spa","data_cid":"990000000000000003","address":"Sector 15, Gurugram, Haryana 122001, India"}
            ]}""";

    private static UsernamePasswordAuthenticationToken as(String role) {
        return new UsernamePasswordAuthenticationToken("t", null, List.of(new SimpleGrantedAuthority(role)));
    }

    private Long admin() {
        return jdbc.queryForObject("SELECT MIN(id) FROM app_user", Long.class);
    }

    private long lead(String cid) {
        return jdbc.queryForObject("SELECT id FROM spa_lead WHERE google_cid=?", Long.class, cid);
    }

    @Test void onlyAdminsOnboardAndOnlyWithPermissionConfirmed() throws Exception {
        String body = "{\"leadIds\":[1],\"permissionConfirmed\":true}";
        for (String role : List.of("ROLE_USER", "ROLE_SPA_OWNER"))
            mvc.perform(post("/api/admin/spa-leads/onboard").with(authentication(as(role))).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/spa-leads/onboard").with(authentication(as("ROLE_ADMIN"))).contentType("application/json")
                        .content("{\"leadIds\":[1],\"permissionConfirmed\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test void onboardedLeadIsLiveAndBookableWithItsGoogleHours() throws Exception {
        leads.importLeads(json.readTree(SEARCH), "test", null);
        long withHours = lead("990000000000000001"), noHours = lead("990000000000000002"), declined = lead("990000000000000003");
        leads.update(declined, "NOT_INTERESTED", null);

        var result = onboarding.onboard(List.of(withHours, noHours, declined), 14, admin());
        assertEquals(2, result.onboarded());
        assertEquals(1, result.skipped(), result.problems().toString());

        long spaId = jdbc.queryForObject("SELECT spa_id FROM spa_lead WHERE id=?", Long.class, withHours);
        Map<String, Object> spa = jdbc.queryForMap("SELECT * FROM spa WHERE id=?", spaId);
        assertEquals("Onboard Test Spa", spa.get("name"));
        assertEquals("ChIJonboardTestSpa0001", spa.get("google_place_id"));
        assertEquals("APPROVED", spa.get("kyc_status"));
        assertEquals(Boolean.TRUE, spa.get("is_active"));
        assertEquals(Boolean.TRUE, spa.get("is_verified"));
        assertEquals("Haryana", spa.get("state"));
        assertEquals("122001", spa.get("postal_code"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM service WHERE spa_id=? AND is_active=1", Integer.class, spaId));
        assertEquals(199900, jdbc.queryForObject("SELECT price_cents FROM service WHERE spa_id=? AND name='Swedish Massage'", Integer.class, spaId));
        // Closed on Wednesdays: six weekly rules, and no Wednesday appointments.
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM schedule_rule WHERE spa_id=?", Integer.class, spaId));
        assertEquals("ONBOARDED", jdbc.queryForObject("SELECT status FROM spa_lead WHERE id=?", String.class, withHours));
        assertNotNull(jdbc.queryForObject("SELECT permission_confirmed_at FROM spa_lead WHERE id=?", Object.class, withHours));
        assertEquals("NOT_INTERESTED", jdbc.queryForObject("SELECT status FROM spa_lead WHERE id=?", String.class, declined));

        long serviceId = jdbc.queryForObject("SELECT id FROM service WHERE spa_id=? AND name='Deep Tissue Massage'", Long.class, spaId);
        LocalDate from = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        var week = availability.getAvailableSlotsForServiceRange(spaId, serviceId, from, 7, 1);
        var first = week.days().stream().filter(d -> d.date().getDayOfWeek() == java.time.DayOfWeek.MONDAY).findFirst().orElseThrow().slots().get(0);
        assertEquals(java.time.LocalTime.of(10, 0), first.startTs().atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalTime(),
                "appointments start at the spa's local opening time");
        int total = 0;
        for (var day : week.days()) {
            int count = day.slots().size();
            total += count;
            switch (day.date().getDayOfWeek()) {
                case WEDNESDAY -> assertEquals(0, count, "closed day");
                case MONDAY, TUESDAY, SATURDAY, SUNDAY -> assertEquals(12, count, day.date() + ": 10 AM–10 PM is twelve hours");
                case THURSDAY -> assertEquals(12, count, "9:30 AM to 9:30 PM");
                case FRIDAY -> assertEquals(12, count, "open 24 hours: 10 AM–10 PM appointments");
            }
        }
        assertEquals(72, total);

        // Guests find it in search under either name for the city.
        for (String city : List.of("Gurgaon", "gurugram"))
            assertTrue(search.searchSpas(city, null, null, null, "Onboard Test Spa", null, null, null, null, null, null, null, null,
                            null, null, null, "name_asc", 0, 10).content().stream().anyMatch(s -> s.id().equals(spaId)), city);

        // A lead without hours gets 10 AM–10 PM every day.
        long other = jdbc.queryForObject("SELECT spa_id FROM spa_lead WHERE id=?", Long.class, noHours);
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM schedule_rule WHERE spa_id=?", Integer.class, other));

        // Onboarding again changes nothing.
        var again = onboarding.onboard(List.of(withHours), 14, admin());
        assertEquals(0, again.onboarded());
        assertEquals(1, again.skipped());
    }
}
