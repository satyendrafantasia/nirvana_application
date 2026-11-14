package com.nirvana.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.Slot;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class TherapistRoster {
    private final JdbcTemplate jdbc;
    private final com.nirvana.application.repository.SpaRepository spas;
    private final ObjectMapper json;

    public enum Kind { WORK, BREAK, TIME_OFF }
    public record Interval(@NotNull Kind kind, @NotNull OffsetDateTime start, @NotNull OffsetDateTime end) {}
    public record Shift(int weekday, LocalTime start, LocalTime end) {}
    public List<Shift> weekly(long therapist) {
        return jdbc.query("SELECT shifts_json FROM therapist_week WHERE therapist_id=?",rs->rs.next()?decodeWeek(rs.getString(1)):null,therapist);
    }
    private List<Shift> decodeWeek(String value){
        try{return json.readValue(value,new TypeReference<List<Shift>>(){});}
        catch(JsonProcessingException e){throw new IllegalStateException("Invalid weekly roster",e);}
    }
    public void saveWeek(long therapist,List<Shift> shifts){
        try{jdbc.update("INSERT INTO therapist_week VALUES(?,?) ON DUPLICATE KEY UPDATE shifts_json=VALUES(shifts_json)",
            therapist,json.writeValueAsString(shifts));}catch(JsonProcessingException e){throw new IllegalStateException(e);}
    }
    public record Key(long therapist, LocalDate date) {}

    public static ZoneId zone(Spa spa) {
        String name = spa.getAddress() == null ? null : spa.getAddress().getTimezone();
        if (name == null || name.isBlank()) name = spa.getTimezone();
        return name == null || name.isBlank() ? ZoneId.systemDefault() : ZoneId.of(name);
    }
    public static OffsetDateTime end(Slot slot) {
        OffsetDateTime minimum = slot.getStartTs().plusMinutes(slot.getService().getDurationMin());
        return minimum.isAfter(slot.getEndTs()) ? minimum : slot.getEndTs();
    }

    public Map<Key,List<Interval>> load(long spa, LocalDate from, LocalDate through) {
        Map<Key,List<Interval>> result = new HashMap<>();
        jdbc.query("""
            SELECT r.* FROM therapist_daily_roster r JOIN therapist t ON t.id=r.therapist_id
            WHERE t.spa_id=? AND r.local_date BETWEEN ? AND ?
            """, rs -> {
                try {
                    result.put(new Key(rs.getLong("therapist_id"),rs.getDate("local_date").toLocalDate()),
                        json.readValue(rs.getString("intervals_json"), new TypeReference<List<Interval>>() {}));
                } catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored roster",ex); }
            },spa,java.sql.Date.valueOf(from),java.sql.Date.valueOf(through));
        ZoneId zone=zone(spas.findById(spa).orElseThrow());
        jdbc.query("SELECT w.* FROM therapist_week w JOIN therapist t ON t.id=w.therapist_id WHERE t.spa_id=?",rs->{
            long id=rs.getLong("therapist_id");var week=decodeWeek(rs.getString("shifts_json"));
            for(LocalDate d=from;!d.isAfter(through);d=d.plusDays(1)){
                Key key=new Key(id,d);
                if(result.containsKey(key))continue;
                List<Interval> intervals=new ArrayList<>();
                for(var shift:week){
                    if(shift.weekday()==d.getDayOfWeek().getValue())
                        intervals.add(new Interval(Kind.WORK,d.atTime(shift.start()).atZone(zone).toOffsetDateTime(),
                            shift.end().isAfter(shift.start())?d.atTime(shift.end()).atZone(zone).toOffsetDateTime():d.plusDays(1).atStartOfDay(zone).toOffsetDateTime()));
                    if(!shift.end().isAfter(shift.start()) && shift.weekday()==d.minusDays(1).getDayOfWeek().getValue())
                        intervals.add(new Interval(Kind.WORK,d.atStartOfDay(zone).toOffsetDateTime(),d.atTime(shift.end()).atZone(zone).toOffsetDateTime()));
                }
                result.put(key,intervals);
            }
        },spa);
        return result;
    }
    public void save(long therapist, LocalDate day, List<Interval> intervals) {
        try {
            jdbc.update("""
                INSERT INTO therapist_daily_roster(therapist_id,local_date,intervals_json) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE intervals_json=VALUES(intervals_json)
                """,therapist,java.sql.Date.valueOf(day),json.writeValueAsString(intervals));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid roster",ex); }
    }

    // A missing date inherits the existing global therapist availability. An explicit empty date is off.
    public static boolean covers(Map<Key,List<Interval>> roster, long therapist, ZoneId zone,
                                  OffsetDateTime start, OffsetDateTime end) {
        while (start.isBefore(end)) {
            LocalDate day = start.atZoneSameInstant(zone).toLocalDate();
            OffsetDateTime midnight = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
            OffsetDateTime until = midnight.isBefore(end) ? midnight : end;
            List<Interval> hours = roster.get(new Key(therapist,day));
            if (hours != null && !coversDay(hours,start,until)) return false;
            start = until;
        }
        return true;
    }
    public static boolean coversDay(List<Interval> hours, OffsetDateTime start, OffsetDateTime end) {
        if (hours.stream().anyMatch(i -> i.kind()!=Kind.WORK && start.isBefore(i.end()) && end.isAfter(i.start()))) return false;
        OffsetDateTime cursor = start;
        for (Interval i : hours.stream().filter(i -> i.kind()==Kind.WORK).sorted(Comparator.comparing(Interval::start)).toList()) {
            if (i.start().isAfter(cursor)) break;
            if (i.end().isAfter(cursor)) cursor = i.end();
            if (!cursor.isBefore(end)) return true;
        }
        return false;
    }
    public boolean permits(Slot slot, long therapist) {
        ZoneId zone = zone(slot.getSpa());
        OffsetDateTime end = end(slot);
        return covers(load(slot.getSpa().getId(),slot.getStartTs().atZoneSameInstant(zone).toLocalDate(),
            end.atZoneSameInstant(zone).toLocalDate()),therapist,zone,slot.getStartTs(),end);
    }
}
