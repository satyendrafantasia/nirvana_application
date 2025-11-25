package com.nirvana.application.controller;

import com.nirvana.application.model.dto.InventoryCalendarResponse;
import com.nirvana.application.model.dto.InventoryDayRequest;
import com.nirvana.application.model.dto.InventoryDayResponse;
import com.nirvana.application.service.InventoryCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryCalendarService inventoryCalendarService;

    @GetMapping("/calendar")
    public InventoryCalendarResponse getCalendar(@RequestParam Long spaId,
                                                 @RequestParam(required = false) Long serviceId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return inventoryCalendarService.getCalendar(spaId, serviceId, startDate, endDate);
    }

    @PostMapping("/day")
    public InventoryDayResponse upsertDay(@Valid @RequestBody InventoryDayRequest request) {
        return inventoryCalendarService.upsertDay(request);
    }
}
