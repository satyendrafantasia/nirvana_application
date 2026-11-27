package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Transactional
public class SupportDeskController {
    private final JdbcTemplate db;
    private static final SecureRandom RANDOM = new SecureRandom();
    public record NewCase(@NotBlank @Size(max=200) String subject, @NotBlank @Size(max=4000) String message) {}
    public record Reply(@NotBlank @Size(max=4000) String message) {}
    public record GuestReceipt(long caseId, String accessKey) {}
    public enum State { OPEN, RESOLVED, CLOSED }
    public record CaseView(long id, String state, Long assigneeId, List<Map<String,Object>> messages) {}

    private static String digest(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @PostMapping("/public/support/cases")
    @ResponseStatus(HttpStatus.CREATED)
    public GuestReceipt open(@Valid @RequestBody NewCase input) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.update("""
                INSERT INTO message_thread(created_at,updated_at,last_message_at,subject,status,unread_count,guest_key_hash)
                VALUES (UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,'OPEN',1,?)
                """,input.subject().trim(),digest(key));
        long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        message(id,null,"USER",input.message());
        return new GuestReceipt(id,key);
    }

    private long guestId(String key) {
        if (key.length()==43) {
            var ids = db.queryForList("SELECT id FROM message_thread WHERE guest_key_hash=? AND user_id IS NULL", Long.class,digest(key));
            if (!ids.isEmpty()) return ids.get(0);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found");
    }

    @GetMapping("/public/support/case")
    public CaseView guest(@RequestHeader("X-Support-Key") String key) { return view(guestId(key),false); }

    @PostMapping("/public/support/case/replies")
    public void guestReply(@RequestHeader("X-Support-Key") String key,@Valid @RequestBody Reply input) {
        long id = guestId(key);
        openForReply(id);
        message(id,null,"USER",input.message());
        db.update("UPDATE message_thread SET unread_count=unread_count+1,updated_at=UTC_TIMESTAMP(6),last_message_at=UTC_TIMESTAMP(6) WHERE id=?",id);
    }

    @GetMapping("/me/support/unread")
    public int unread() {
        return db.queryForObject("SELECT COUNT(*) FROM message_thread WHERE user_id=? AND customer_unread=1",Integer.class,SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/me/support/{id}")
    public CaseView customer(@PathVariable long id) {
        var ids = db.queryForList("SELECT id FROM message_thread WHERE id=? AND user_id=?", Long.class, id, SecurityUtils.getCurrentUserId());
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found");
        return view(id,false);
    }

    @PostMapping("/me/support/{id}/read")
    public void markRead(@PathVariable long id,@RequestParam long through) {
        // A reply arriving between opening and acknowledging the conversation stays unread.
        db.update("""
            UPDATE message_thread SET customer_unread=0 WHERE id=? AND user_id=? AND NOT EXISTS
            (SELECT 1 FROM chat_message WHERE thread_id=? AND sender_type IN ('AGENT','SYSTEM') AND id>?)
            """,id,SecurityUtils.getCurrentUserId(),id,through);
    }

    @GetMapping("/admin/support/cases")
    @PreAuthorize("@staffAccess.admin()")
    public List<Map<String,Object>> queue(@RequestParam(defaultValue="OPEN") State state,
            @RequestParam(defaultValue="0") int page) {
        if (page<0 || page>100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        return db.queryForList("""
            SELECT t.id,t.subject,t.status,t.assigned_agent_id AS assigneeId,a.name AS assignee,
                   b.booking_reference AS bookingReference,t.user_id IS NULL AS guest
            FROM message_thread t LEFT JOIN booking b ON b.id=t.booking_id LEFT JOIN app_user a ON a.id=t.assigned_agent_id
            WHERE t.status=? ORDER BY t.updated_at DESC,t.id DESC LIMIT 40 OFFSET ?
            """,state.name(),page*40);
    }

    @GetMapping("/admin/support/cases/{id}")
    @PreAuthorize("@staffAccess.admin()")
    public CaseView staffView(@PathVariable long id) { return view(id,true); }

    private CaseView view(long id,boolean staff) {
        var states=db.queryForList("SELECT status,assigned_agent_id FROM message_thread WHERE id=?",id);
        if(states.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found");
        var messages=db.queryForList("""
            SELECT id,sender_type AS senderType,content,created_at AS createdAt FROM chat_message
            WHERE thread_id=? AND moderation_status <> 'REJECTED' ORDER BY id
            """,id);
        var row=states.get(0);
        Long assignee=staff && row.get("assigned_agent_id") instanceof Number n ? n.longValue() : null;
        return new CaseView(id,(String)row.get("status"),assignee,messages);
    }

    @PostMapping("/admin/support/cases/{id}/assign")
    @PreAuthorize("@staffAccess.admin()")
    public void assign(@PathVariable long id,@RequestParam(required=false) Long takeOverFrom) {
        var row=lockCase(id);
        long actor=SecurityUtils.getCurrentUserId();
        Long owner=row.get("assigned_agent_id") instanceof Number n ? n.longValue() : null;
        if (owner != null && owner==actor) return;
        if (!java.util.Objects.equals(owner,takeOverFrom)) throw changedAssignee();
        db.update("UPDATE message_thread SET assigned_agent_id=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",actor,id);
    }

    @PostMapping("/admin/support/cases/{id}/replies")
    @PreAuthorize("@staffAccess.admin()")
    public void reply(@PathVariable long id,@Valid @RequestBody Reply input) {
        var row=lockForStaff(id);
        ensureOpen(row);
        long agent=SecurityUtils.getCurrentUserId();
        message(id,agent,"AGENT",input.message());
        db.update("""
            UPDATE message_thread SET assigned_agent_id=COALESCE(assigned_agent_id,?),customer_unread=1,
            unread_count=0,last_message_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?
            """,agent,id);
    }

    @PutMapping("/admin/support/cases/{id}/state")
    @PreAuthorize("@staffAccess.admin()")
    public void state(@PathVariable long id,@RequestParam State state) {
        String previous=(String)lockForStaff(id).get("status");
        if (previous.equals(state.name())) return;
        long agent=SecurityUtils.getCurrentUserId();
        message(id,agent,"SYSTEM","Case status changed to "+state.name());
        db.update("UPDATE message_thread SET status=?,customer_unread=1,assigned_agent_id=COALESCE(assigned_agent_id,?),updated_at=UTC_TIMESTAMP(6) WHERE id=?",state.name(),agent,id);
    }

    private ResponseStatusException changedAssignee() {
        return new ResponseStatusException(HttpStatus.CONFLICT,"Another agent owns this case. Refresh the conversation before taking it over.");
    }
    private Map<String,Object> lockForStaff(long id) {
        var row=lockCase(id);
        if (row.get("assigned_agent_id") instanceof Number n && n.longValue()!=SecurityUtils.getCurrentUserId()) throw changedAssignee();
        return row;
    }
    private Map<String,Object> lockCase(long id) {
        var rows=db.queryForList("SELECT status,assigned_agent_id FROM message_thread WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found");
        return rows.get(0);
    }
    private void openForReply(long id) {
        ensureOpen(lockCase(id));
    }
    private void ensureOpen(Map<String,Object> row) {
        if (!"OPEN".equals(row.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"This case is closed. Open a new case or ask a team member to reopen it.");
    }
    private void message(long id,Long actor,String type,String content) {
        db.update("""
            INSERT INTO chat_message(created_at,updated_at,thread_id,sender_user_id,sender_type,content,moderation_status)
            VALUES(UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?,?,?,'APPROVED')
            """,id,actor,type,content.trim());
    }
}
