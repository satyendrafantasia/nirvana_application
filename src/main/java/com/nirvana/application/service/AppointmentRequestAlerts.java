package com.nirvana.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class AppointmentRequestAlerts {
    private final JdbcTemplate db;
    private final NotificationService mail;
    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}") private String frontendUrl;

    public void enqueue(long id, String event) {
        db.update("""
            INSERT INTO booking_alert_job(request_id,event_kind,audience,revision)
            VALUES(?,?,?,0) ON DUPLICATE KEY UPDATE id=id
            """,id,event,"REQUEST_NEW".equals(event) ? "SPA_EMAIL" : "CUSTOMER_EMAIL");
    }

    public void expire(long id) {
        if (db.update("UPDATE appointment_request SET state='EXPIRED' WHERE id=? AND state='PENDING' AND deadline<=UTC_TIMESTAMP()",id)>0)
            enqueue(id,"REQUEST_EXPIRED");
    }

    @Scheduled(fixedDelayString="${booking.requests.expiry-ms:60000}")
    @Transactional
    public void expireDue() {
        var due=db.queryForList("SELECT id FROM appointment_request WHERE state='PENDING' AND deadline<=UTC_TIMESTAMP() ORDER BY deadline,id LIMIT 100 FOR UPDATE SKIP LOCKED",Long.class);
        for (long id : due) expire(id);
    }

    public boolean deliver(long id, String event) {
        var row=db.queryForMap("""
            SELECT r.state,CAST(r.deadline>UTC_TIMESTAMP() AS UNSIGNED) AS live,s.name,
            COALESCE(NULLIF(s.email,''),m.email) AS owner_email,u.email AS guest_email
            FROM appointment_request r JOIN spa s ON s.id=r.spa_id JOIN app_user u ON u.id=r.user_id
            LEFT JOIN spa_manager sm ON sm.id=s.spa_manager_id LEFT JOIN app_user m ON m.id=sm.user_id
            WHERE r.id=?
            """,id);
        String expected=switch(event) {
            case "REQUEST_NEW" -> "PENDING";
            case "REQUEST_DECLINED" -> "DECLINED";
            case "REQUEST_EXPIRED" -> "EXPIRED";
            default -> throw new IllegalArgumentException("Unknown appointment request alert");
        };
        boolean owner="PENDING".equals(expected);
        if (!expected.equals(row.get("state")) || (owner && ((Number)row.get("live")).intValue()==0)) return false;
        String to=(String)row.get(owner ? "owner_email" : "guest_email");
        if (to==null || to.isBlank()) throw new BookingContactUnavailable("No appointment request email");
        String subject=switch(event) {
            case "REQUEST_NEW" -> "New appointment request";
            case "REQUEST_DECLINED" -> "Spa declined your appointment request";
            default -> "Your appointment request expired";
        };
        String action=owner ? "Open the appointment requests in your spa dashboard to accept or decline."
                : "Open My bookings to see the request. You can choose another time or request an available time again.";
        String url=HtmlUtils.htmlEscape(frontendUrl.replaceAll("/+$","")+(owner ? "/owner" : "/me/bookings"));
        mail.sendRequestUpdate(to,subject,"<p>"+HtmlUtils.htmlEscape(subject)+" · "+
                HtmlUtils.htmlEscape(String.valueOf(row.get("name")))+" · #"+id+"</p><p>"+action+"</p><p><a href=\""+url+"\">Open Nirvana</a></p>");
        return true;
    }
}
