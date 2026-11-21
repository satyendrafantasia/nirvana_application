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
             {"title":"Onboard No Hours Spa","data_cid":"990000000000000002","address":"Sector 14, Gurugram, Haryana 122007, India",
              "website":"https://www.google.com/search?q=LONG_QUERY"},
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

    @Test void onboardedLeadIsLiveWithStandardInventory() throws Exception {
        leads.importLeads(json.readTree(SEARCH.replace("LONG_QUERY", "x".repeat(300))), "test", null);
        long withHours = lead("990000000000000001"), noHours = lead("990000000000000002"), declined = lead("990000000000000003");
        leads.update(declined, "NOT_INTERESTED", null);

        var result = onboarding.onboard(List.of(withHours, noHours, declined), 21, admin());
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
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM service WHERE spa_id=? AND is_active=1", Integer.class, spaId));
        assertEquals(200000, jdbc.queryForObject("SELECT price_cents FROM service WHERE spa_id=? AND name='Swedish Massage'", Integer.class, spaId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM service WHERE spa_id=? AND duration_min=45 AND price_cents=150000", Integer.class, spaId));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM standard_room_service m JOIN spa_room r ON r.id=m.room_id WHERE r.spa_id=?", Integer.class, spaId));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM therapist WHERE spa_id=? AND is_active=1", Integer.class, spaId));
        assertEquals("ONBOARDED", jdbc.queryForObject("SELECT status FROM spa_lead WHERE id=?", String.class, withHours));
        // Usable with every active Nirvana package.
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM packages WHERE is_active=1", Integer.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM service_package_spa x JOIN packages p ON p.id=x.package_id WHERE p.is_active=1 AND x.spa_id=?", Integer.class, spaId));
        assertNotNull(jdbc.queryForObject("SELECT permission_confirmed_at FROM spa_lead WHERE id=?", Object.class, withHours));
        assertEquals("NOT_INTERESTED", jdbc.queryForObject("SELECT status FROM spa_lead WHERE id=?", String.class, declined));

        long serviceId = jdbc.queryForObject("SELECT id FROM service WHERE spa_id=? AND name='Deep Tissue Massage'", Long.class, spaId);
        LocalDate from = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        var week = availability.getAvailableSlotsForServiceRange(spaId, serviceId, from, 7, 1);
        var first = week.days().stream().filter(d -> d.date().getDayOfWeek() == java.time.DayOfWeek.MONDAY).findFirst().orElseThrow().slots().get(0);
        assertEquals(java.time.LocalTime.of(11, 0), first.startTs().atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalTime(),
                "appointments start at the spa's local opening time");
        int total = 0;
        for (var day : week.days()) {
            int count = day.slots().size();
            total += count;
            assertEquals(10,count);
        }
        assertEquals(70,total);

        // Guests find it in search under either name for the city.
        for (String city : List.of("Gurgaon", "gurugram"))
            assertTrue(search.searchSpas(city, null, null, null, "Onboard Test Spa", null, null, null, null, null, null, null, null,
                            null, null, null, "name_asc", 0, 10).content().stream().anyMatch(s -> s.id().equals(spaId)), city);

        assertEquals(java.util.Set.of("delhi", "new delhi"),
                com.nirvana.application.repository.spec.SpaAddressSpecifications.cityNames(" New Delhi "));
        assertEquals(java.util.Set.of("pune", "poona"),
                com.nirvana.application.repository.spec.SpaAddressSpecifications.cityNames("Pune"));
        assertEquals(java.util.Set.of("lucknow"),
                com.nirvana.application.repository.spec.SpaAddressSpecifications.cityNames("Lucknow"));

        // Standard hours apply even when the source omits them.
        long other = jdbc.queryForObject("SELECT spa_id FROM spa_lead WHERE id=?", Long.class, noHours);
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM spa_working_day WHERE spa_id=?", Integer.class, other));
        // Its website was a search link too long for the spa's field: onboarded without it.
        assertNull(jdbc.queryForObject("SELECT website_url FROM spa WHERE id=?", String.class, other));

        // Running out of appointment times: the top-up refills the booking window from the spa's hours.
        String lastDay = "SELECT DATE(CONVERT_TZ(MAX(start_ts),'+00:00','+05:30')) FROM slot WHERE spa_id=?";
        java.time.LocalDate cut = from.plusDays(6);
        jdbc.update("DELETE FROM generated_room_slot WHERE room_id IN (SELECT id FROM spa_room WHERE spa_id=?) AND DATE(original_start) > ?", spaId, cut);
        jdbc.update("DELETE FROM slot WHERE spa_id=? AND DATE(CONVERT_TZ(start_ts,'+00:00','+05:30')) > ?", spaId, cut);
        assertEquals(cut, jdbc.queryForObject(lastDay, java.time.LocalDate.class, spaId));
        // Retain one manually scheduled appointment well beyond the gap.
        jdbc.update("INSERT INTO slot (created_at,updated_at,spa_id,service_id,room_id,start_ts,end_ts,capacity_unit,booked_units,is_blocked,status,version) SELECT NOW(),NOW(),spa_id,service_id,room_id,DATE_ADD(start_ts, INTERVAL 40 DAY),DATE_ADD(end_ts, INTERVAL 40 DAY),1,0,0,'OPEN',0 FROM slot WHERE spa_id=? LIMIT 1", spaId);
        var later = availability.getAvailableSlotsForServiceRange(spaId, serviceId, cut.plusDays(1), 7, 1);
        assertEquals(70, later.days().stream().mapToInt(d -> d.slots().size()).sum(), "standard hours every day");
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT room_id, start_ts FROM slot WHERE spa_id=? GROUP BY room_id, start_ts HAVING COUNT(*) > 1) d""", Integer.class, spaId));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM slot WHERE spa_id=?", Long.class, spaId);
        availability.getAvailableSlotsForServiceRange(spaId, serviceId, cut.plusDays(1), 7, 1);
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM slot WHERE spa_id=?", Long.class, spaId), "a second run adds nothing");

        // Onboarding again changes nothing.
        var again = onboarding.onboard(List.of(withHours), 14, admin());
        assertEquals(0, again.onboarded());
        assertEquals(1, again.skipped());
    }
}
