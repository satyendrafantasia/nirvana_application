package com.nirvana.application.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Google Maps' hours text ("10 AM–10 PM", "Open 24 hours", "Closed") into a weekly schedule. */
final class LeadHours {
    /** Hours used when Google listed none. */
    static final Day DEFAULT = new Day(LocalTime.of(10, 0), LocalTime.of(22, 0), false);
    /** Appointment times for spas open around the clock. */
    private static final LocalTime ALL_DAY_FIRST = LocalTime.of(10, 0), ALL_DAY_LAST_END = LocalTime.of(22, 0);
    /** No appointments start before or end after these, whatever the listed hours. */
    private static final LocalTime EARLIEST = LocalTime.of(7, 0), LATEST_END = LocalTime.of(23, 0);
    private static final Pattern TIME = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*([AaPp]\\.?[Mm]\\.?)?");
    private static final Pattern RANGE = Pattern.compile(TIME.pattern() + "\\s*(?:[–—-]|to)\\s*" + TIME.pattern());

    private LeadHours() {}

    /** Open from {@code open} until {@code close}; a close at or before the open runs past midnight. */
    record Day(LocalTime open, LocalTime close, boolean allDay) {
        /** Start times of one-hour appointments that fit inside the day's hours. */
        List<LocalTime> slotStarts(int minutes) {
            LocalTime first = allDay ? ALL_DAY_FIRST : open.isBefore(EARLIEST) ? EARLIEST : open;
            // Minutes after midnight, so a close past midnight stays later than the open.
            int end = allDay ? ALL_DAY_LAST_END.toSecondOfDay() / 60
                    : close.isAfter(open) ? close.toSecondOfDay() / 60 : 24 * 60;
            end = Math.min(end, LATEST_END.toSecondOfDay() / 60);
            List<LocalTime> starts = new ArrayList<>();
            for (int at = first.toSecondOfDay() / 60; at + minutes <= end; at += minutes) starts.add(LocalTime.of(at / 60, at % 60));
            return starts;
        }
    }

    /** Each weekday's hours, absent when closed. Days Google left out get the default hours. */
    static Map<DayOfWeek, Day> week(Map<String, String> listed) {
        Map<DayOfWeek, Day> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            String text = listed == null ? null : listed.get(d.name().toLowerCase(Locale.ROOT));
            if (text == null) { week.put(d, DEFAULT); continue; }
            Optional<Day> day = parse(text);
            if (day == null) week.put(d, DEFAULT);
            else day.ifPresent(v -> week.put(d, v));
        }
        return week;
    }

    /** Empty when closed; null when the text can't be read. */
    static Optional<Day> parse(String text) {
        String t = text.strip().toLowerCase(Locale.ROOT);
        if (t.contains("24 hours")) return Optional.of(new Day(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, true));
        if (t.startsWith("closed")) return Optional.empty();
        // Split hours such as "10 AM–2 PM, 4–10 PM" are read from the first open to the last close.
        List<LocalTime[]> ranges = new ArrayList<>();
        Matcher m = RANGE.matcher(text);
        while (m.find()) {
            String closeMeridiem = m.group(6);
            LocalTime close = time(m.group(4), m.group(5), closeMeridiem);
            LocalTime open = time(m.group(1), m.group(2), m.group(3) != null ? m.group(3) : closeMeridiem);
            if (open == null || close == null) return null;
            ranges.add(new LocalTime[]{open, close});
        }
        if (ranges.isEmpty()) return null;
        return Optional.of(new Day(ranges.get(0)[0], ranges.get(ranges.size() - 1)[1], false));
    }

    private static LocalTime time(String hour, String minute, String meridiem) {
        int h = Integer.parseInt(hour), min = minute == null ? 0 : Integer.parseInt(minute);
        if (min > 59) return null;
        if (meridiem != null) {
            if (h < 1 || h > 12) return null;
            boolean pm = Character.toLowerCase(meridiem.charAt(0)) == 'p';
            h = h % 12 + (pm ? 12 : 0);
        } else if (h > 23) {
            return null;
        }
        return LocalTime.of(h, min);
    }
}
