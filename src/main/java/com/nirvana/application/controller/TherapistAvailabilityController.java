package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SlotAvailabilityResponse;
import com.nirvana.application.service.impl.SlotAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/spas/{spaId}/services/{serviceId}/therapists")
@RequiredArgsConstructor
public class TherapistAvailabilityController {

    private final SlotAvailabilityService slotAvailabilityService;

    private final com.nirvana.application.repository.ServiceRepository services;
    private final com.nirvana.application.repository.TherapistRepository therapists;
    public record Named(long id,String name,String type){}
    public record Directory(List<Named> named,List<String> labels){}
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Directory directory(@PathVariable Long spaId,@PathVariable Long serviceId){
        var service=services.findByIdAndSpa_Id(serviceId,spaId).orElseThrow(jakarta.persistence.EntityNotFoundException::new);
        var spa=service.getSpa();
        if(!Boolean.TRUE.equals(service.getIsActive()) || !Boolean.TRUE.equals(spa.getIsVerified()) || !Boolean.TRUE.equals(spa.getIsActive()))return new Directory(List.of(),List.of());
        var team=therapists.findActiveAvailableForSpaAndService(spaId,serviceId);
        boolean labels=Boolean.TRUE.equals(spa.getAllowTherapistTypeSelection());
        return new Directory(Boolean.TRUE.equals(spa.getAllowTherapistSelection())?team.stream().filter(t->t.getPublicName()!=null && !t.getPublicName().isBlank())
            .map(t->new Named(t.getId(),t.getPublicName(),labels?t.getType():null)).sorted(java.util.Comparator.comparing(Named::name)).toList():List.of(),
            labels?team.stream().map(t->t.getType()).filter(v->v!=null && !v.isBlank()).distinct().sorted().toList():List.of());
    }
    @GetMapping("/availability")
    public List<SlotAvailabilityResponse> getTherapistAvailability(
            @PathVariable Long spaId,
            @PathVariable Long serviceId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return slotAvailabilityService.getAvailableSlotsForServiceOnDate(spaId, serviceId, date,1);
    }
}
