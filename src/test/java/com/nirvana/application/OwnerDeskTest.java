package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class OwnerDeskTest {
    @Autowired MockMvc mvc;
    @Autowired com.nirvana.application.service.SpaPackageUsageService usage;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private UsernamePasswordAuthenticationToken owner(long user) {
        var p=new UserPrincipal(user,"desk","desk@example.test","",true,List.of(new SimpleGrantedAuthority("ROLE_SPA_OWNER")));
        return new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities());
    }
    long spa(){return db.queryForObject("SELECT MIN(s.id) FROM spa s JOIN spa_manager m ON m.id=s.spa_manager_id",Long.class);}
    long user(long spa){return db.queryForObject("SELECT m.user_id FROM spa s JOIN spa_manager m ON m.id=s.spa_manager_id WHERE s.id=?",Long.class,spa);}
    @Test void cumulativeRefundEventsAreCountedOnce(){
        var payment=new com.nirvana.application.model.Payment();
        payment.setRefundedCents(100);
        payment.setRefundedCents(100);
        payment.setRefundedCents(250);
        assertEquals(2,payment.getRefundPostings().size());
        assertEquals(150,payment.getRefundPostings().get(250).getAmountCents());
    }
    @Test void annualBucketsAndOwnerIsolation() throws Exception {
        long spa=spa(); String path="/api/owner/spas/"+spa+"/desk/summary";
        var raw=mvc.perform(get(path).param("period","YEAR").with(authentication(owner(user(spa)))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var data=json.readTree(raw); long sum=0;
        for(var bucket:data.get("trend")) sum+=bucket.get("bookings").asLong();
        assertEquals(data.at("/counts/bookings").longValue(),sum);
        assertTrue(data.get("trend").size()>=12);
        mvc.perform(get(path).with(authentication(owner(-99)))).andExpect(status().isForbidden());
    }
    @Test void reportsExportsAndCustomerReadsAreSpaScoped() throws Exception {
        long spa=spa();
        for(String resource:List.of("packages","capacity","customers/1","transactions.csv")){
            String path="/api/owner/spas/"+spa+"/desk/"+resource;
            mvc.perform(get(path).with(authentication(owner(user(spa))))).andExpect(status().isOk());
            mvc.perform(get(path).with(authentication(owner(-99)))).andExpect(status().isForbidden());
        }
    }
    @Test void archivedOfferPreservesFiveSessionPurchaseAndCountsOneRedemption() throws Exception {
        long spa=spa(),owner=user(spa);
        long customer=db.queryForObject("SELECT MIN(u.id) FROM app_user u WHERE NOT EXISTS (SELECT 1 FROM user_spa_package_subscription x WHERE x.spa_id=? AND x.user_id=u.id)",Long.class,spa);
        var payload=java.util.Map.of("level","GOLD","price",2500,"freeSessionsCount",5,"status","ACTIVE");
        String path="/api/v1/spa/"+spa+"/packages";
        var offer=mvc.perform(post(path).contentType("application/json").content(json.writeValueAsBytes(payload))
            .with(authentication(owner(owner)))).andExpect(status().isOk()).andReturn();
        long id=json.readTree(offer.getResponse().getContentAsByteArray()).get("id").asLong();
        em.flush();
        db.update("""
            INSERT INTO user_spa_package_subscription(created_at,updated_at,user_id,spa_id,spa_package_id,level,price_paid,total_sessions,remaining_sessions,status,purchase_date,payment_status,currency,version)
            VALUES(UTC_TIMESTAMP(),UTC_TIMESTAMP(),?,?,?,'GOLD',2500,5,5,'ACTIVE',UTC_TIMESTAMP(),'SUCCESS','INR',0)
            """,customer,spa,id);
        var paused=new java.util.HashMap<String,Object>(payload);paused.put("status","INACTIVE");paused.put("archived",true);paused.put("price",3000);
        mvc.perform(post(path).contentType("application/json").content(json.writeValueAsBytes(paused)).with(authentication(owner(owner)))).andExpect(status().isOk());
        usage.consumeSession(customer,spa,null,"Test visit");em.flush();
        assertEquals(4,db.queryForObject("SELECT remaining_sessions FROM user_spa_package_subscription WHERE user_id=? AND spa_id=?",Integer.class,customer,spa));
        assertEquals(2500,db.queryForObject("SELECT price_paid FROM user_spa_package_subscription WHERE user_id=? AND spa_id=?",Integer.class,customer,spa));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_spa_package_usage_log WHERE spa_id=? AND user_id=?",Integer.class,spa,customer));
        mvc.perform(delete("/api/owner/spas/"+spa+"/desk/packages/"+id).with(authentication(owner(owner)))).andExpect(status().isConflict());
    }
    @Test void allRecordViewsCanBeRead() throws Exception {
        long spa=spa();
        for(String view:List.of("APPOINTMENTS","VISITORS","BUYERS","TRANSACTIONS"))
            mvc.perform(get("/api/owner/spas/"+spa+"/desk/records").param("view",view).param("period","YEAR")
                .with(authentication(owner(user(spa))))).andExpect(status().isOk());
    }
}
