package com.nirvana.application.controller;

import com.nirvana.application.model.dto.WaitlistRequest;
import com.nirvana.application.model.dto.WaitlistResponse;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public WaitlistResponse joinWaitlist(@Valid @RequestBody WaitlistRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return waitlistService.joinWaitlist(userId, request);
    }
}
