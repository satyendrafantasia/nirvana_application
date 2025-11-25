package com.nirvana.application.controller;

import com.nirvana.application.model.dto.BlackoutWindowRequest;
import com.nirvana.application.model.dto.BlackoutWindowResponse;
import com.nirvana.application.service.BlackoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/blackouts")
@RequiredArgsConstructor
public class BlackoutController {

    private final BlackoutService blackoutService;

    @PostMapping
    public BlackoutWindowResponse createBlackout(@Valid @RequestBody BlackoutWindowRequest request) {
        return blackoutService.createBlackout(request);
    }

    @GetMapping
    public List<BlackoutWindowResponse> list(@RequestParam Long spaId) {
        return blackoutService.listForSpa(spaId);
    }
}
