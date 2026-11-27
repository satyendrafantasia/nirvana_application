package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc
class OwnerScheduleTest {
    @org.junit.jupiter.api.BeforeEach void publishFixtureName(){jdbc.update("update therapist set public_name='Maya' where id=1");}
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    static final String BASE="/api/owner/spas/1/schedule";
    static final ZoneId ZONE=ZoneId.of("Europe/London");
    final LocalDate day=LocalDate.now(ZONE).plusMonths(2).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    String time(int hour,int minute){return day.atTime(hour,minute).atZone(ZONE).toOffsetDateTime().toString();}
    record Reply(int status,JsonNode body){}
    Reply call(MockHttpServletRequestBuilder req,long user,String role,Object body) throws Exception {
        var principal=new UserPrincipal(user,"schedule","schedule@example.test","",true,List.of(new SimpleGrantedAuthority(role)));
        req.with(authentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities())));
        if(body!=null)req.contentType("application/json").content(json.writeValueAsString(body));
        var response=mvc.perform(req).andReturn().getResponse();
        return new Reply(response.getStatus(),json.readTree(response.getContentAsString()));
    }
    JsonNode ok(Reply r){assertEquals(200,r.status(),()->String.valueOf(r.body()));return r.body();}
    Reply owner(MockHttpServletRequestBuilder req,Object body)throws Exception{return call(req,2,"ROLE_SPA_OWNER",body);}
    long room() throws Exception {return ok(owner(post(BASE+"/rooms"),Map.of("name","Treatment room","active",true))).get("id").asLong();}
    long slot(int h,int m,int capacity)throws Exception {
        return ok(owner(post(BASE+"/slots"),Map.of("roomId",room(),"serviceId",1,"start",time(h,m),"end",time(h+1,m),"capacity",capacity))).get("id").asLong();
    }
    Reply book(long slot,boolean named)throws Exception {
        Map<String,Object> body=new HashMap<>(Map.of("slotId",slot,"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE"));
        if(named)body.put("therapistId",1);
        return call(post("/api/bookings"),3,"ROLE_USER",body);
    }
    JsonNode lookup(long slot)throws Exception{return ok(owner(get(BASE+"/available-therapists").param("serviceId","1").param("slotId",""+slot),null));}
    long matches(long slot) throws Exception {return lookup(slot).findValues("therapistId").stream().filter(n->n.asLong()==1).count();}
    Map<String,String> interval(String kind,int start,int end){return Map.of("kind",kind,"start",time(start,0),"end",time(end,0));}
    Reply hours(List<?> list)throws Exception{return owner(put(BASE+"/therapists/1/days/"+day),Map.of("intervals",list));}
    @Test @Transactional void frontDeskReceiptAndMovePreserveCustomerAndCapacity() throws Exception {
        ok(hours(List.of(interval("WORK",10,18))));
        long first=slot(11,0,1), next=slot(15,0,1);
        var result=book(first,true);
        assertEquals(2,result.status()/100,()->result.body().toString());
        long booking=result.body().get("bookingId").asLong();
        String desk="/api/owner/spas/1/desk/bookings/"+booking;
        jdbc.update("INSERT INTO spa_booking_confirmation(booking_id,spa_id,token,status,created_at,updated_at) VALUES(?,1,?,'PENDING',UTC_TIMESTAMP(),UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE status='PENDING'",booking,UUID.randomUUID().toString().replace("-",""));
        assertEquals(200,owner(post(desk+"/reply"),Map.of("accept",true)).status());
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM spa_booking_confirmation WHERE booking_id=?",String.class,booking));
        ok(owner(post(desk+"/move"),Map.of("slotId",next,"reason","Guest requested change")));
        entityManager.flush();
        assertEquals(1L,jdbc.queryForObject("SELECT therapist_id FROM booking WHERE id=?",Long.class,booking));
        assertEquals(next,jdbc.queryForObject("SELECT slot_id FROM booking WHERE id=?",Long.class,booking));
        for(int i=0;i<2;i++)assertEquals(200,owner(post(desk+"/collect"),Map.of("method","CASH","reference","Desk test")).status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM spa_desk_receipt WHERE booking_id=?",Integer.class,booking));
        assertEquals(403,call(post(desk+"/collect"),3,"ROLE_USER",Map.of("method","CASH")).status());
        ok(owner(post("/api/owner/spas/1/bookings/"+booking+"/cancel"),Map.of("reason","Test cancellation")));
        entityManager.flush();
        for(int i=0;i<2;i++)assertEquals(200,owner(post(desk+"/return"),null).status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM spa_desk_receipt WHERE booking_id=? AND returned_at IS NOT NULL",Integer.class,booking));

    }
    @Test @Transactional void theSpaMovesPartPaysAndShareTheDeskWithLimitedStaff() throws Exception {
        ok(hours(List.of(interval("WORK",10,18))));
        long[] slots={slot(11,0,1),slot(12,0,1),slot(13,0,1),slot(15,0,1)};
        var result=book(slots[0],false);
        assertEquals(2,result.status()/100,()->result.body().toString());
        long booking=result.body().get("bookingId").asLong();
        String desk="/api/owner/spas/1/desk/bookings/"+booking;
        // Three moves by the spa: more than a guest may make, and none of them use up the guest's own changes.
        for(int i=1;i<slots.length;i++)ok(owner(post(desk+"/move"),Map.of("slotId",slots[i],"reason","Therapist running late")));
        entityManager.flush();
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(DISTINCT revision) FROM booking_alert_job WHERE booking_id=? AND event_kind='RESCHEDULED'",Integer.class,booking));
        assertEquals(slots[3],jdbc.queryForObject("SELECT slot_id FROM booking WHERE id=?",Long.class,booking));
        assertEquals(0,jdbc.queryForObject("SELECT COALESCE(reschedule_count,0) FROM booking WHERE id=?",Integer.class,booking));

        int balance=jdbc.queryForObject("SELECT remainder_cents FROM booking WHERE id=?",Integer.class,booking);
        org.junit.jupiter.api.Assumptions.assumeTrue(balance>100,"the fixture service needs a price");
        // An advance, sent twice by a flaky connection, is recorded once.
        for(int i=0;i<2;i++)ok(owner(post(desk+"/collect"),Map.of("method","UPI","amountCents",100,"requestKey","advance-"+booking)));
        assertEquals(400,owner(post(desk+"/collect"),Map.of("method","CASH","amountCents",balance,"requestKey","too-much-"+booking)).status());
        java.util.function.Supplier<MockHttpServletRequestBuilder> records=()->get("/api/owner/spas/1/desk/records").param("period","CUSTOM").param("from",day.toString()).param("to",day.toString());
        var row=ok(owner(records.get(),null)).at("/items/0");
        assertEquals("DUE_AT_SPA",row.get("payment").asText());
        assertEquals(balance-100,row.get("payable").asInt());
        ok(owner(post(desk+"/collect"),Map.of("method","CASH","tipCents",50,"requestKey","rest-"+booking)));
        row=ok(owner(records.get(),null)).at("/items/0");
        assertEquals("PAID_AT_SPA",row.get("payment").asText());
        assertEquals(50,row.get("deskTips").asInt());

        // Front-desk staff: the day's appointments yes; the money, voids and returns no.
        long staff=jdbc.queryForObject("SELECT MIN(id) FROM app_user WHERE id NOT IN (2,3) AND email IS NOT NULL",Long.class);
        String email=jdbc.queryForObject("SELECT email FROM app_user WHERE id=?",String.class,staff);
        ok(owner(post("/api/owner/spas/1/staff"),Map.of("contact",email.toUpperCase())));
        assertEquals(200,call(records.get(),staff,"ROLE_SPA_STAFF",null).status());
        var summary=ok(call(get("/api/owner/spas/1/desk/summary"),staff,"ROLE_SPA_STAFF",null));
        assertEquals("DESK",summary.get("access").asText());
        assertFalse(summary.has("money"));
        assertEquals(403,call(get("/api/owner/spas/1/desk/transactions.csv"),staff,"ROLE_SPA_STAFF",null).status());
        assertEquals(403,call(post(desk+"/void"),staff,"ROLE_SPA_STAFF",Map.of("reason","Mistake")).status());
        assertEquals(403,call(get("/api/owner/spas/1/desk/records").param("view","TRANSACTIONS"),staff,"ROLE_SPA_STAFF",null).status());
        assertEquals(403,call(get("/api/owner/spas/2/desk/records"),staff,"ROLE_SPA_STAFF",null).status());
        assertEquals(1,ok(call(get("/api/owner/desk-spas"),staff,"ROLE_SPA_STAFF",null)).size());

        // The owner voids the last part, recorded by mistake: the balance is due again.
        ok(owner(post(desk+"/void"),Map.of("reason","Recorded as cash, was UPI")));
        row=ok(owner(records.get(),null)).at("/items/0");
        assertEquals("DUE_AT_SPA",row.get("payment").asText());
        assertEquals(100,row.get("deskPaid").asInt());
        var full=ok(owner(get("/api/owner/spas/1/desk/summary"),null));
        assertEquals("OWNER",full.get("access").asText());
        assertTrue(full.has("payout"));
        assertTrue(full.get("money").toString().contains("DESK"));

        assertEquals(200,owner(delete("/api/owner/spas/1/staff/"+staff),null).status());
        assertEquals(403,call(records.get(),staff,"ROLE_SPA_STAFF",null).status());

        // A guest who signed up by phone and has no name still shows among visitors.
        jdbc.update("UPDATE app_user SET name=NULL WHERE id=3");
        jdbc.update("UPDATE booking SET status='COMPLETED' WHERE id=?",booking);
        var visitors=ok(owner(get("/api/owner/spas/1/desk/records").param("view","VISITORS").param("period","CUSTOM")
            .param("from",day.toString()).param("to",day.toString()),null));
        assertTrue(java.util.stream.StreamSupport.stream(visitors.get("items").spliterator(),false).anyMatch(v->v.get("id").asLong()==3));
    }
    @Test @Transactional void checkoutRetriesAndDeskBookingsShareInventory() throws Exception {
        ok(hours(List.of(interval("WORK",10,18))));
        long first=slot(11,0,1), second=slot(14,0,1);
        var payload=new HashMap<String,Object>(Map.of("slotId",first,"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE","requestKey","checkout-test"));
        long id=ok(call(post("/api/bookings"),3,"ROLE_USER",payload)).get("bookingId").asLong();
        assertEquals(id,ok(call(post("/api/bookings"),3,"ROLE_USER",payload)).get("bookingId").asLong());
        payload.put("slotId",second);
        assertEquals(409,call(post("/api/bookings"),3,"ROLE_USER",payload).status());
        var choices=ok(owner(get("/api/owner/spas/1/desk-rooms").param("service","1").param("date",day.toString()),null));
        assertTrue(choices.findValues("id").stream().anyMatch(v->v.asLong()==second));
        var team=ok(owner(get("/api/owner/spas/1/desk-slots/"+second+"/therapists"),null));
        assertFalse(team.isEmpty());
        var desk=Map.of("therapistId",team.get(0).get("id").asLong(),"slotId",second,"name","Reception guest","contact","demo","channel","PHONE","requestKey","phone-test");
        long walk=ok(owner(post("/api/owner/spas/1/desk-appointments"),desk)).asLong();
        assertEquals(walk,ok(owner(post("/api/owner/spas/1/desk-appointments"),desk)).asLong());
        assertEquals(409,book(second,false).status());
    }

    @Test @Transactional void importedModesRequireOwnerAcceptance() throws Exception {
        ok(hours(List.of(interval("WORK",10,18))));
        long target=slot(13,0,1);
        var spa=entityManager.find(com.nirvana.application.model.Spa.class,1L);
        spa.setBookingMode("ENQUIRY"); entityManager.flush();
        assertEquals(409,book(target,false).status());
        spa.setBookingMode("REQUEST");entityManager.flush();
        long id=ok(call(post("/api/spas/1/appointment-requests"),3,"ROLE_USER",Map.of("slotId",target))).get("id").asLong();
        assertEquals(0,jdbc.queryForObject("SELECT booked_units FROM slot WHERE id=?",Integer.class,target));
        ok(owner(post("/api/owner/spas/1/appointment-requests/"+id),Map.of("accept",true)));
        assertEquals("ACCEPTED",jdbc.queryForObject("select state from appointment_request where id=?",String.class,id));
        assertEquals(409,owner(post("/api/owner/spas/1/appointment-requests/"+id),Map.of("accept",true)).status());
    }

    @Test @Transactional void expiredOrDeclinedRequestCanBeRequestedAgainAndAlertsAreRecoverable() throws Exception {
        ok(hours(List.of(interval("WORK",10,18))));
        long target=slot(13,0,1);
        var spa=entityManager.find(com.nirvana.application.model.Spa.class,1L);
        spa.setBookingMode("REQUEST"); entityManager.flush();
        String request="/api/spas/1/appointment-requests";
        var params=Map.of("slotId",target);
        long first=ok(call(post(request),3,"ROLE_USER",params)).get("id").asLong();
        assertEquals(first,ok(call(post(request),3,"ROLE_USER",params)).get("id").asLong());
        jdbc.update("UPDATE appointment_request SET deadline=UTC_TIMESTAMP()-INTERVAL 1 MINUTE WHERE id=?",first);
        long second=ok(call(post(request),3,"ROLE_USER",params)).get("id").asLong();
        assertNotEquals(first,second);
        assertEquals("EXPIRED",jdbc.queryForObject("SELECT state FROM appointment_request WHERE id=?",String.class,first));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_alert_job WHERE request_id=? AND event_kind='REQUEST_EXPIRED'",Integer.class,first));
        ok(owner(post("/api/owner/spas/1/appointment-requests/"+second),Map.of("accept",false)));
        long third=ok(call(post(request),3,"ROLE_USER",params)).get("id").asLong();
        assertNotEquals(second,third);
        long alert=jdbc.queryForObject("SELECT id FROM booking_alert_job WHERE request_id=? AND event_kind='REQUEST_DECLINED'",Long.class,second);
        jdbc.update("UPDATE booking_alert_job SET state='NEEDS_CONTACT' WHERE id=?",alert);
        String action="/api/admin/operations/booking-alerts/"+alert;
        assertEquals(403,call(post(action+"/assign"),3,"ROLE_USER",Map.of()).status());
        assertEquals(409,call(post(action+"/retry"),2,"ROLE_ADMIN",Map.of()).status());
        ok(call(post(action+"/assign"),2,"ROLE_ADMIN",Map.of()));
        ok(call(post(action+"/retry"),2,"ROLE_ADMIN",Map.of()));
        assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM booking_alert_job WHERE id=?",String.class,alert));
        jdbc.update("UPDATE booking_alert_job SET state='FAILED' WHERE id=?",alert);
        ok(call(post(action+"/resolve"),2,"ROLE_ADMIN",Map.of("note","Guest contacted by phone")));
        assertEquals("RESOLVED",jdbc.queryForObject("SELECT state FROM booking_alert_job WHERE id=?",String.class,alert));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM booking_alert_action WHERE job_id=?",Integer.class,alert));
    }

    @Test @Transactional void dailyCloseAndTransferHistory() throws Exception {
        String path="/api/owner/spas/1/finance/";
        var input=new HashMap<String,Object>(Map.of("date",LocalDate.now(ZONE).minusDays(1).toString(),"currency","EUR","cash",100,"reason","Drawer count"));
        ok(owner(post(path+"SHIFT"),input));
        long id=ok(owner(get(path+"SHIFT"),null)).get(0).get("id").asLong();
        ok(owner(post(path+"shift/"+id+"/approve"),null));
        assertEquals("APPROVED",ok(owner(get(path+"SHIFT"),null)).get(0).get("state").asText());
        input.put("reference","qa-transfer");input.put("transferDate",input.get("date"));input.put("transferred",100);
        assertEquals(403,owner(post(path+"PAYOUT"),input).status());
        ok(call(post(path+"PAYOUT"),2,"ROLE_ADMIN",input));
        assertEquals("EXCEPTION",ok(owner(get(path+"PAYOUT"),null)).get(0).get("state").asText());
    }

    @Test @Transactional void lunchAndLaterBookingUseEntireDuration() throws Exception {
        ok(hours(List.of(interval("WORK",10,18),interval("BREAK",13,14))));
        long noon=slot(12,0,3), lunch=slot(12,30,3), after=slot(14,0,3), earlier=slot(13,30,3);
        assertTrue(ok(owner(get(BASE).param("date",day.toString()),null)).get("therapists").findValues("working").stream().anyMatch(JsonNode::asBoolean));
        assertEquals(1,matches(noon));
        assertEquals(0,matches(lunch));
        var customer=ok(call(get("/api/spas/1/services/1/slots").param("date",day.toString()),3,"ROLE_USER",null));
        for (long id : List.of(noon,lunch)) {
            if(id==lunch && !customer.toString().contains("\"slotId\":"+id)) continue;
            var result=java.util.stream.StreamSupport.stream(customer.spliterator(),false)
                    .filter(s -> s.get("slotId").asLong()==id).findFirst().orElseThrow();
            assertEquals(id==noon,result.findValues("therapistId").stream().anyMatch(n -> n.asLong()==1));
        }
        assertEquals(409,book(lunch,true).status());
        assertEquals(1,matches(after));
        assertTrue(book(after,true).status()/100==2);
        assertEquals(409,hours(List.of()).status());
        ok(hours(List.of(interval("WORK",10,18))));
        assertEquals(0,matches(earlier));
        assertEquals(409,book(earlier,true).status());
    }
    @Test @Transactional void namedAndLabelHoldsRespectVisibilityAndKeepTheirAssignment() throws Exception {
        String directory="/api/spas/1/services/1/therapists";
        jdbc.update("update spa set allow_therapist_selection=1,allow_therapist_type_selection=1 where id=1");
        jdbc.update("update therapist set is_available=0 where spa_id=1 and id<>1");
        jdbc.update("update therapist set type='Thai' where id=1");
        var anonymous=ok(owner(post("/api/owner/spas/1/therapists"),Map.of("name","Private employee",
            "type","Thai","active",true,"available",true,"serviceIds",List.of(1)))).get("id").asLong();
        var team=ok(call(get(directory),3,"ROLE_USER",null));
        assertEquals(1,team.get("named").size());
        assertEquals("Maya",team.at("/named/0/name").asText());
        long a=slot(11,0,1), b=slot(11,0,1);
        var input=new HashMap<String,Object>(Map.of("spaId",1,"serviceId",1,"slotId",a,"guests",1,"therapistId",1));
        var held=call(post("/api/spas/1/services/1/slots/"+a+"/hold"),3,"ROLE_USER",input);
        assertEquals(2,held.status()/100,()->held.body().toString());
        assertEquals("Maya · Thai",held.body().get("therapistName").asText());
        assertEquals(409,book(b,true).status());
        input.put("holdToken",held.body().get("holdToken").asText());input.put("paymentMode","OFFLINE");
        var named=call(post("/api/bookings"),3,"ROLE_USER",input);
        assertEquals(2,named.status()/100,()->named.body().toString());
        assertEquals("Maya · Thai",named.body().get("therapistName").asText());
        ok(owner(put("/api/owner/spas/1/therapist-visibility"),Map.of("names",false,"labels",true)));
        assertTrue(ok(call(get(directory),3,"ROLE_USER",null)).get("named").isEmpty());
        var slots=ok(call(get(directory+"/availability").param("date",day.toString()),3,"ROLE_USER",null));
        assertTrue(slots.findValues("therapistId").isEmpty());
        assertEquals(409,book(b,true).status());
        input.remove("holdToken");input.remove("therapistId");input.put("therapistType","Thai");input.put("slotId",b);
        var label=call(post("/api/bookings"),3,"ROLE_USER",input);
        assertEquals(2,label.status()/100,()->label.body().toString());
        assertEquals("Thai",label.body().get("therapistName").asText());
        assertEquals(anonymous,jdbc.queryForObject("select therapist_id from booking where id=? and therapist_preference='LABEL'",
            Long.class,label.body().get("bookingId").asLong()));
        var history=ok(call(get("/api/me/bookings"),3,"ROLE_USER",null));
        assertFalse(history.toString().contains("Maya"));
        assertFalse(history.toString().contains("Private employee"));
    }

    @Test @Transactional void bookedRoomRejectsClosureAndSecondBooking() throws Exception {
        long id=slot(12,0,3);
        assertEquals(2,book(id,false).status()/100);
        assertEquals(409,book(id,false).status());
        assertEquals(409,owner(patch(BASE+"/slots/"+id),Map.of("closed",true)).status());
        assertTrue(lookup(id).isEmpty());
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test @Transactional void overnightAppointmentUsesBothRosterDates() throws Exception {
        jdbc.update("update schedule_rule set open_local='19:00',close_local='02:00' where spa_id=1 and weekday=1");
        var midnight = day.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
        String nextDay = BASE+"/therapists/1/days/"+day.plusDays(1);
        var work = Map.of("kind","WORK","start",midnight,"end",midnight.plusHours(2));
        long id=ok(owner(post(BASE+"/slots"),Map.of("roomId",room(),"serviceId",1,"capacity",1,
                "start",midnight.minusMinutes(30),"end",midnight.plusMinutes(30)))).get("id").asLong();
        ok(owner(put(nextDay),Map.of("intervals",List.of(work))));
        assertEquals(1,matches(id));
        var nextSchedule=ok(owner(get(BASE).param("date",day.plusDays(1).toString()),null));
        assertTrue(nextSchedule.get("slots").findValues("id").stream().anyMatch(n->n.asLong()==id));
        ok(owner(put(nextDay),Map.of("intervals",List.of())));
        assertEquals(0,matches(id));
        assertEquals(409,book(id,true).status());
    }
    @Test @Transactional void generationIsRepeatableAndSlotAssignmentIsUsedAtCheckout() throws Exception {
        long room=room();
        var body=Map.of("serviceId",1,"roomIds",List.of(room),"date",day.toString(),"start","10:00","end","13:00","minutes",60);
        assertEquals(3,ok(owner(post(BASE+"/generate"),body)).get("created").asInt());
        var generate=BASE+"/prepare";
        var generated=ok(owner(post(generate).param("date",day.toString()),null)).get("slots");
        assertTrue(generated.size()>2);
        var choices=java.util.stream.StreamSupport.stream(generated.spliterator(),false)
            .filter(s->s.get("roomId").asLong()==room).toList();
        long id=choices.get(0).get("id").asLong();
        var path=BASE+"/slots/"+id;
        long closedSlot=choices.get(1).get("id").asLong(), movedSlot=choices.get(2).get("id").asLong();
        ok(owner(patch(BASE+"/slots/"+closedSlot),Map.of("closed",true)));
        ok(owner(patch(BASE+"/slots/"+movedSlot),Map.of("start",time(15,0),"end",time(16,0))));
        assertEquals(409,owner(patch(BASE+"/slots/"+movedSlot),Map.of("start",time(10,30),"end",time(11,30))).status());
        assertTrue(ok(owner(get(path+"/therapists"),null)).findValues("id").stream().anyMatch(n->n.asLong()==1));
        ok(owner(patch(path),Map.of("therapistId",1,"capacity",2)));
        assertEquals(2,book(id,false).status()/100);
        assertTrue(lookup(id).isEmpty());
        assertEquals(409,owner(patch(path),Map.of("therapistId",0)).status());
        assertEquals(409,book(id,false).status());
        assertEquals(409,owner(patch(path),Map.of("closed",true)).status());
        assertEquals(3,ok(owner(post(BASE+"/generate"),body)).get("skipped").size());
        var repeated=ok(owner(post(generate).param("date",day.toString()),null)).get("slots");
        assertEquals(generated.size(),repeated.size());
        var edited=java.util.stream.StreamSupport.stream(repeated.spliterator(),false)
                .filter(s->s.get("id").asLong()==id).findFirst().orElseThrow();
        assertFalse(edited.get("closed").asBoolean());
        assertEquals(1,edited.get("therapistId").asLong());
    }

    @Test @Transactional void scarceTherapistsAndRoomsAndLegacyConflicts() throws Exception {
        // Isolate the eligible staff set from fixtures created by other committed race tests.
        jdbc.update("update therapist set is_available=0 where spa_id=1 and id<>1");
        ok(owner(post("/api/owner/spas/1/therapists"),Map.of("name","Second therapist","available",true,"active",true,"serviceIds",List.of(1))));
        long a=slot(10,0,1), b=slot(10,0,1), c=slot(10,0,1);
        var first=book(a,false);assertEquals(2,first.status()/100);
        assertEquals(2,book(b,false).status()/100);
        assertEquals(409,book(c,false).status());
        long booking=first.body().get("bookingId").asLong();
        assertEquals(409,call(post("/api/bookings/"+booking+"/reschedule"),3,"ROLE_USER",Map.of("targetSlotId",b)).status());
        assertEquals(409,book(a,false).status());
        assertEquals(2,call(post("/api/bookings/"+booking+"/cancel"),3,"ROLE_USER",null).status()/100);
        assertEquals(2,book(c,false).status()/100);
        jdbc.update("update booking set therapist_id=null,room_id=null where slot_id=?",c);
        assertTrue(lookup(a).isEmpty());
    }
    @Test @Transactional void partialBusyAndWeeklyOverrides() throws Exception {
        long id=ok(owner(post("/api/owner/spas/1/therapists"),Map.of("name","Weekly therapist","available",true,"active",true,"serviceIds",List.of(1)))).get("id").asLong();
        String path=BASE+"/therapists/"+id;
        ok(owner(put(path+"/week"),List.of(Map.of("weekday",1,"start","10:00","end","18:00"))));
        ok(owner(put(path+"/days/"+day),Map.of("intervals",List.of(interval("WORK",10,18),Map.of("kind","TIME_OFF","start",time(11,15),"end",time(12,15))))));
        for(int[] sample:List.of(new int[]{11,0,0},new int[]{12,0,0},new int[]{12,15,1})){
            assertEquals(sample[2],lookup(slot(sample[0],sample[1],1)).findValues("therapistId").stream().filter(n->n.asLong()==id).count());
        }
    }
    @Test @Transactional void holdsProtectTimeAndCapacity() throws Exception {
        long id=slot(11,0,2);
        String path=BASE+"/slots/"+id;
        var held=call(post("/api/spas/1/services/1/slots/"+id+"/hold"),3,"ROLE_USER",
            Map.of("spaId",1,"serviceId",1,"slotId",id,"guests",1,"holdMinutes",10));
        assertEquals(2,held.status()/100,()->String.valueOf(held.body()));
        assertEquals(409,owner(patch(path),Map.of("closed",true)).status());
        assertEquals(409,owner(patch(path),Map.of("end",time(13,0))).status());
        assertEquals(1,ok(owner(patch(path),Map.of("capacity",2))).get("held").asInt());
        long competing=slot(11,0,1);
        var reserved=jdbc.queryForObject("select therapist_id from booking_hold where slot_id=?",Long.class,id);
        var named=Map.of("slotId",competing,"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE","therapistId",reserved);
        assertEquals(409,call(post("/api/bookings"),3,"ROLE_USER",named).status());
        jdbc.update("update booking_hold set expires_at='2000-01-01' where slot_id=?",id);
        assertEquals(2,call(post("/api/bookings"),3,"ROLE_USER",named).status()/100);
    }
    @Test @Transactional void foreignRecordsAndRolesAreRejected() throws Exception {
        assertEquals(403,call(get(BASE),3,"ROLE_SPA_OWNER",null).status());
        assertEquals(403,call(get(BASE),2,"ROLE_USER",null).status());
        long other=ok(owner(post("/api/onboarding/spas"),Map.of("name","Second schedule","phone","1234567890"))).get("id").asLong();
        String otherSchedule="/api/owner/spas/"+other+"/schedule";
        long existingSlot=slot(15,0,1);
        assertEquals(404,owner(patch(otherSchedule+"/slots/"+existingSlot),Map.of("closed",true)).status());
        assertEquals(404,owner(put(otherSchedule+"/therapists/1/days/"+day),Map.of("intervals",List.of())).status());
        assertEquals(404,owner(get(otherSchedule+"/available-therapists").param("serviceId","1").param("slotId",""+existingSlot),null).status());
        long foreignRoom=ok(owner(post(otherSchedule+"/rooms"),Map.of("name","Other spa","active",true))).get("id").asLong();
        assertEquals(404,owner(patch(BASE+"/slots/"+existingSlot),Map.of("roomId",foreignRoom)).status());
        assertEquals(404,owner(put(BASE+"/rooms/"+foreignRoom),Map.of("active",false,"name","Other spa")).status());
        assertEquals(404,owner(put(BASE+"/therapists/999999/days/"+day),Map.of("intervals",List.of())).status());
        assertEquals(404,owner(patch(BASE+"/slots/999999"),Map.of("closed",true)).status());
    }
    @Test void racingDeskMovesKeepTheLosingAppointment() throws Exception {
        long therapist=ok(owner(post("/api/owner/spas/1/therapists"),Map.of("publicName","Test desk","name","Desk race fixture","available",true,"active",true,"serviceIds",List.of(1)))).get("id").asLong();
        long room=room();var start=day.plusWeeks(2).atTime(10,0).atZone(ZONE).toOffsetDateTime();
        long[] slots=new long[3], ids=new long[2];
        for(int i=0;i<3;i++){
            var at=start.plusHours(i*2L);
            slots[i]=ok(owner(post(BASE+"/slots"),Map.of("serviceId",1,"roomId",room,"start",at,"end",at.plusHours(1),"capacity",1))).get("id").asLong();
            if(i<2){
                var r=call(post("/api/bookings"),3,"ROLE_USER",Map.of("slotId",slots[i],"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE","therapistId",therapist));
                assertEquals(2,r.status()/100);ids[i]=r.body().get("bookingId").asLong();
            }
        }
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            List<Future<Reply>> moves=new ArrayList<>();
            for(long id:ids)moves.add(pool.submit(()->{gate.await();return owner(post("/api/owner/spas/1/desk/bookings/"+id+"/move"),Map.of("slotId",slots[2]));}));
            gate.countDown();int success=0;
            for(int i=0;i<2;i++){
                var result=moves.get(i).get(20,TimeUnit.SECONDS);
                if(result.status()==200)success++;else assertEquals(409,result.status(),()->String.valueOf(result.body()));
                assertEquals(result.status()==200?slots[2]:slots[i],jdbc.queryForObject("SELECT slot_id FROM booking WHERE id=?",Long.class,ids[i]));
            }
            assertEquals(1,success);
        }finally{pool.shutdownNow();}
    }
    @Test void simultaneousRequestsCannotShareRoomOrTherapist() throws Exception {
        for(boolean sameRoom:List.of(true,false)){
            var at=day.plusWeeks(sameRoom?3:4).atTime(11,0).atZone(ZONE).toOffsetDateTime();
            long[] slots=new long[2],staff=new long[2];
            for(int i=0;i<2;i++){
                staff[i]=ok(owner(post("/api/owner/spas/1/therapists"),Map.of("publicName","Concurrent","name","Concurrent therapist","available",true,"active",true,"serviceIds",List.of(1)))).get("id").asLong();
                slots[i]=ok(owner(post(BASE+"/slots"),Map.of("serviceId",1,"roomId",room(),"start",at,"end",at.plusHours(1),"capacity",2))).get("id").asLong();
            }
            var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
            try{
                List<Future<Reply>> replies=new ArrayList<>();
                for(int i=0;i<2;i++){int n=i;replies.add(pool.submit(()->{gate.await();return call(post("/api/bookings"),3,"ROLE_USER",
                    Map.of("slotId",slots[sameRoom?0:n],"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE","therapistId",staff[sameRoom?n:0]));}));}
                gate.countDown();int wins=0;
                for(var future:replies){var result=future.get(20,TimeUnit.SECONDS);if(result.status()/100==2)wins++;else assertEquals(409,result.status());}
                assertEquals(1,wins);
            }finally{pool.shutdownNow();}
        }
    }
    @Test void concurrentTimeOffAndNamedBookingHaveOneWinner() throws Exception {
        // Use a separate date because this test commits on two independent connections.
        LocalDate raceDay=day.plusWeeks(1);
        var start=raceDay.atTime(16,0).atZone(ZONE).toOffsetDateTime();
        // Independent reruns use an unassigned therapist and a new spa-local date.
        var serviceIds=List.of(1);
        var therapist=ok(owner(post("/api/owner/spas/1/therapists"),Map.of("publicName","Race","name","Race therapist",
                "available",true,"active",true,"serviceIds",serviceIds)));
        long tid=therapist.get("id").asLong();
        String path=BASE+"/therapists/"+tid+"/days/"+raceDay;
        long id=ok(owner(post(BASE+"/slots"),Map.of("roomId",room(),"serviceId",1,"start",start,"end",start.plusHours(1),"capacity",1))).get("id").asLong();
        var gate=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var block=pool.submit(()->{gate.await();return owner(put(path),Map.of("intervals",List.of()));});
            var create=pool.submit(()->{gate.await();return call(post("/api/bookings"),3,"ROLE_USER",
                    Map.of("slotId",id,"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE","therapistId",tid));});
            gate.countDown();
            Reply blocked=block.get(20,TimeUnit.SECONDS), booked=create.get(20,TimeUnit.SECONDS);
            if (blocked.status()==200) {
                assertEquals(409,booked.status(),()->booked.body().toString());
            } else {
                assertEquals(409,blocked.status(),()->blocked.body().toString());
                assertEquals(2,booked.status()/100,()->booked.body().toString());
                assertEquals(409,owner(put(path),Map.of("intervals",List.of())).status());
            }
            assertFalse(lookup(id).findValues("therapistId").stream().anyMatch(n->n.asLong()==tid));
        } finally {pool.shutdownNow();}
    }
}
