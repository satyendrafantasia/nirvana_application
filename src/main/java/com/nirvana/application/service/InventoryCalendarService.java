package com.nirvana.application.service;

import com.nirvana.application.model.InventoryCalendarEntry;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.InventoryCalendarResponse;
import com.nirvana.application.model.dto.InventoryDayRequest;
import com.nirvana.application.model.dto.InventoryDayResponse;
import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.repository.InventoryCalendarRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryCalendarService {

    private final InventoryCalendarRepository inventoryCalendarRepository;
    private final SpaRepository spaRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderAuditService providerAuditService;
    private final AuditTrailService auditTrailService;

    @Transactional(readOnly = true)
    public InventoryCalendarResponse getCalendar(Long spaId, Long serviceId, LocalDate startDate, LocalDate endDate) {
        List<InventoryCalendarEntry> entries = (serviceId == null)
                ? inventoryCalendarRepository.findBySpa_IdAndServiceDateBetween(spaId, startDate, endDate)
                : inventoryCalendarRepository.findBySpa_IdAndService_IdAndServiceDateBetween(spaId, serviceId, startDate, endDate);

        List<InventoryDayResponse> days = entries.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new InventoryCalendarResponse(days);
    }

    @Transactional
    public InventoryDayResponse upsertDay(InventoryDayRequest request) {
        Spa spa = spaRepository.findById(request.getSpaId())
                .orElseThrow(() -> new IllegalArgumentException("Spa not found for inventory"));

        com.nirvana.application.model.Service service = null;
        if (request.getServiceId() != null) {
            service = serviceRepository.findByIdAndSpa_Id(request.getServiceId(), request.getSpaId())
                    .orElseThrow(() -> new IllegalArgumentException("Service not found"));
        }

        Optional<InventoryCalendarEntry> existing = (service == null)
                ? inventoryCalendarRepository.findBySpa_IdAndServiceIsNullAndServiceDate(request.getSpaId(), request.getServiceDate())
                : inventoryCalendarRepository.findBySpa_IdAndService_IdAndServiceDate(request.getSpaId(), service.getId(), request.getServiceDate());

        InventoryCalendarEntry entry = existing.orElseGet(InventoryCalendarEntry::new);
        var beforeState = existing
                .map(e -> snapshot(e))
                .orElse(Map.of());
        entry.setSpa(spa);
        entry.setService(service);
        entry.setServiceDate(request.getServiceDate());
        entry.setAvailableUnits(request.getAvailableUnits());
        entry.setBasePriceCents(request.getBasePriceCents());
        entry.setOverridePriceCents(request.getOverridePriceCents());
        entry.setLocked(Boolean.TRUE.equals(request.getLocked()));
        entry.setNote(request.getNote());
        entry.setCurrency(service == null ? spa.getDefaultCurrency() : service.getCurrency());

        InventoryCalendarEntry saved = inventoryCalendarRepository.save(entry);
        providerAuditService.log(spa.getId(), "SYSTEM", null, "INVENTORY_CALENDAR_UPSERT",
                "Updated inventory for " + request.getServiceDate());
        auditTrailService.record(
                AdminActionType.UPDATE,
                "INVENTORY",
                saved.getId(),
                null,
                beforeState,
                snapshot(saved),
                "Inventory update for spa " + spa.getId());
        return toResponse(saved);
    }

    private Map<String,Object> snapshot(InventoryCalendarEntry e) {
        Map<String,Object> state = new java.util.HashMap<>();
        state.put("availableUnits",e.getAvailableUnits());state.put("basePriceCents",e.getBasePriceCents());
        state.put("overridePriceCents",e.getOverridePriceCents());state.put("locked",e.getLocked());return state;
    }
    private InventoryDayResponse toResponse(InventoryCalendarEntry entry) {
        InventoryDayResponse response = new InventoryDayResponse();
        response.setId(entry.getId());
        response.setSpaId(entry.getSpa().getId());
        response.setServiceId(entry.getService() != null ? entry.getService().getId() : null);
        response.setServiceDate(entry.getServiceDate());
        response.setAvailableUnits(entry.getAvailableUnits());
        response.setBasePriceCents(entry.getBasePriceCents());
        response.setOverridePriceCents(entry.getOverridePriceCents());
        response.setCurrency(entry.getCurrency());
        response.setLocked(entry.getLocked());
        response.setNote(entry.getNote());
        return response;
    }
}
