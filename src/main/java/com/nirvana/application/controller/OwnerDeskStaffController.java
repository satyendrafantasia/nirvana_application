package com.nirvana.application.controller;

import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.security.StaffAccess;
import com.nirvana.application.service.AuditTrailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/**
 * The owner's front-desk team. A member signs in with their own Nirvana account and can run the day's
 * appointments and take payments at that spa, without the money reports, returns, voids or settings.
 */
@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerDeskStaffController {
    private final NamedParameterJdbcTemplate db;
    private final AuditTrailService audit;

    public record Member(@NotBlank @Size(max=255) String contact) {}

    @GetMapping("/spas/{spa}/staff")
    @PreAuthorize("@staffAccess.spa(#spa)")
    public List<Map<String,Object>> list(@PathVariable long spa) {
        return db.queryForList("""
            SELECT u.id,COALESCE(NULLIF(TRIM(u.name),''),u.phone,u.email) AS name,u.email,u.phone,
              CONCAT(DATE_FORMAT(m.added_at,'%Y-%m-%dT%H:%i:%s'),'Z') AS addedUtc
            FROM spa_desk_staff m JOIN app_user u ON u.id=m.user_id WHERE m.spa_id=:spa ORDER BY m.added_at
            """, Map.of("spa", spa));
    }

    @PostMapping("/spas/{spa}/staff")
    @PreAuthorize("@staffAccess.spa(#spa)")
    @Transactional
    public Map<String,Object> add(@PathVariable long spa, @Valid @RequestBody Member input) {
        long user = find(input.contact().trim());
        long actor = SecurityUtils.getCurrentUserId();
        if (user == actor) throw new ResponseStatusException(HttpStatus.CONFLICT, "That's your own account. You already have full access.");
        var args = Map.<String,Object>of("spa", spa, "user", user, "actor", actor, "role", StaffAccess.DESK_ROLE);
        db.update("INSERT IGNORE INTO spa_desk_staff(spa_id,user_id,added_by,added_at) VALUES(:spa,:user,:actor,UTC_TIMESTAMP(6))", args);
        db.update("INSERT INTO user_roles(user_id,role) SELECT :user,:role FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id=:user AND role=:role)", args);
        audit.record(AdminActionType.CREATE, "SPA_DESK_STAFF", user, actor, null, Map.of("spa", spa), "Front-desk access given");
        return Map.of("id", user);
    }

    @DeleteMapping("/spas/{spa}/staff/{user}")
    @PreAuthorize("@staffAccess.spa(#spa)")
    @Transactional
    public void remove(@PathVariable long spa, @PathVariable long user) {
        var args = Map.<String,Object>of("spa", spa, "user", user, "role", StaffAccess.DESK_ROLE);
        if (db.update("DELETE FROM spa_desk_staff WHERE spa_id=:spa AND user_id=:user", args) == 0) return;
        // The desk role goes once they are on no spa's front desk.
        db.update("DELETE FROM user_roles WHERE user_id=:user AND role=:role AND NOT EXISTS (SELECT 1 FROM spa_desk_staff WHERE user_id=:user)", args);
        audit.record(AdminActionType.DELETE, "SPA_DESK_STAFF", user, SecurityUtils.getCurrentUserId(), Map.of("spa", spa), null, "Front-desk access removed");
    }

    /** The spas whose front desk the signed-in staff member runs. */
    @GetMapping("/desk-spas")
    @PreAuthorize("hasAuthority('" + StaffAccess.DESK_ROLE + "')")
    public List<Map<String,Object>> mySpas() {
        return db.queryForList("""
            SELECT s.id,s.name,s.is_active AS active FROM spa_desk_staff m JOIN spa s ON s.id=m.spa_id
            WHERE m.user_id=:user ORDER BY s.name
            """, Map.of("user", SecurityUtils.getCurrentUserId()));
    }

    /** An existing Nirvana account, by email or phone (an Indian 10-digit number may omit +91). */
    private long find(String contact) {
        List<Long> ids;
        if (contact.contains("@")) {
            ids = db.queryForList("SELECT id FROM app_user WHERE LOWER(email)=LOWER(:c)", Map.of("c", contact), Long.class);
        } else {
            String digits = contact.replaceAll("[^0-9]", "");
            if (digits.length() < 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an email address or a full phone number");
            String e164 = digits.length() == 10 ? "+91" + digits : "+" + digits;
            ids = db.queryForList("SELECT id FROM app_user WHERE phone IN (:forms)", Map.of("forms", List.of(e164, digits, contact)), Long.class);
        }
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No Nirvana account uses that email or phone. Ask them to sign up first, then add them.");
        return ids.get(0);
    }
}
