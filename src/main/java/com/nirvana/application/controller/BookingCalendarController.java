package com.nirvana.application.controller;

import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.calendar.BookingCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** "Add to calendar" for the signed-in guest. Another guest's booking reads as not found. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingCalendarController {

    static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar; charset=utf-8");

    private final BookingCalendar calendar;

    @GetMapping("/me/bookings/{booking}/calendar.ics")
    public ResponseEntity<byte[]> invite(@PathVariable long booking) {
        var ics = calendar.forGuest(booking, SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return ResponseEntity.ok()
                .contentType(CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(ics.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(ics.content());
    }
}
