package com.nirvana.application.service.impl;

import com.nirvana.application.model.Spa;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Materialize requested dates while holding the same spa schedule lock used by owner edits. */
@Component
@RequiredArgsConstructor
public class StandardAppointmentSlots {
    private final JdbcTemplate jdbc;

    public void fill(Spa spa, Long serviceId, List<TherapistRoster.Interval> hours) {
        if (hours.isEmpty()) return;
        var rooms = jdbc.queryForList("""
                SELECT r.id room, v.id service, v.duration_min minutes, v.buffer_min buffer
                FROM standard_room_service m JOIN spa_room r ON r.id=m.room_id
                JOIN service v ON v.id=m.service_id WHERE r.spa_id=? AND r.is_active=1
                AND v.is_active=1 AND (? IS NULL OR v.id=?)
                """, spa.getId(), serviceId, serviceId);
        Instant now = Instant.now();
        for (var room : rooms) {
            long id = ((Number)room.get("room")).longValue();
            int minutes = ((Number)room.get("minutes")).intValue();
            if (minutes < 1) continue;
            int step = minutes + Math.max(0, ((Number)room.get("buffer")).intValue());
            for (var interval : hours) {
                Timestamp from = Timestamp.from(interval.start().toInstant()), to = Timestamp.from(interval.end().toInstant());
                var reserved = new HashSet<>(jdbc.queryForList("SELECT original_start FROM generated_room_slot WHERE room_id=? AND original_start>=? AND original_start<?", Timestamp.class, id, from, to));
                for (OffsetDateTime start=interval.start(); !start.plusMinutes(minutes).isAfter(interval.end()); start=start.plusMinutes(step)) {
                    Timestamp key = Timestamp.from(start.toInstant());
                    Timestamp at = Timestamp.valueOf(start.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
                    Timestamp end = Timestamp.valueOf(start.plusMinutes(minutes).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
                    if (!start.toInstant().isAfter(now) || reserved.contains(key)) continue;
                    if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM slot WHERE room_id=? AND start_ts<? AND end_ts>?)", Boolean.class,id,end,at))) continue;
                    if (jdbc.update("INSERT IGNORE INTO generated_room_slot(room_id,original_start) VALUES (?,?)",id,key)==0) continue;
                    jdbc.update("""
                            INSERT INTO slot(created_at,updated_at,spa_id,service_id,room_id,start_ts,end_ts,capacity_unit,booked_units,is_blocked,status,version)
                            VALUES(UTC_TIMESTAMP(),UTC_TIMESTAMP(),?,?,?,?,?,1,0,0,'OPEN',0)
                            """,spa.getId(),room.get("service"),id,at,end);
                }
            }
        }
    }
}
