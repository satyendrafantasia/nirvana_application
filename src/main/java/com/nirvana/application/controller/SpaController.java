package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SpaRequestDTO;
import com.nirvana.application.model.dto.SpaResponseDTO;
import com.nirvana.application.service.SpaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/spas")
@RequiredArgsConstructor
public class SpaController {

    private final SpaService spaService;

    @PostMapping
    public SpaResponseDTO createSpa(@Valid @RequestBody SpaRequestDTO request) {
        return spaService.createSpa(request);
    }

    @GetMapping("/{id}")
    public SpaResponseDTO getSpa(@PathVariable Long id) {
        return spaService.getSpaById(id);
    }

    @GetMapping
    public Page<SpaResponseDTO> listSpas(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 100));
        return spaService.listSpas(pageable);
    }

    @PutMapping("/{id}")
    public SpaResponseDTO updateSpa(@PathVariable Long id,
                                    @Valid @RequestBody SpaRequestDTO request) {
        return spaService.updateSpa(id, request);
    }

    // Soft delete / deactivate
    @DeleteMapping("/{id}")
    public void deactivateSpa(@PathVariable Long id) {
        spaService.deactivateSpa(id);
    }
}
