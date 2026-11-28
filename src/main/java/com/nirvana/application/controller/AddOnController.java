package com.nirvana.application.controller;

import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.addons.BookingAddOns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * The spa's add-on menu: the owner (or a manager) edits it in the catalog, the front desk reads it,
 * and guests see the active add-ons that apply to the treatment they are booking.
 */
@RestController
@RequiredArgsConstructor
public class AddOnController {
    private final BookingAddOns addOns;
    private final SpaRepository spas;
    private final ServiceRepository services;

    public record AddOnInput(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                             @NotNull @Min(0) @Max(10_000_000) Integer priceCents,
                             @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                             @NotNull @Min(0) @Max(BookingAddOns.MAX_EXTRA_MINUTES) Integer extraMinutes,
                             @NotNull Boolean active, @Min(0) @Max(10_000) Integer sortOrder,
                             @Size(max = 200) Set<@NotNull Long> serviceIds, Long version) {
        BookingAddOns.Input toInput() {
            return new BookingAddOns.Input(name, description, priceCents, currency, extraMinutes, active,
                    sortOrder == null ? 0 : sortOrder, serviceIds, version);
        }
    }

    /** For guests: no service scope or version, since those are the owner's business. */
    public record GuestAddOn(long id, String name, String description, int priceCents, String currency, int extraMinutes) {
        static GuestAddOn of(BookingAddOns.AddOn a) {
            return new GuestAddOn(a.id(), a.name(), a.description(), a.priceCents(), a.currency(), a.extraMinutes());
        }
    }

    // ---------------------------------------------------------------- owner catalog

    @GetMapping("/api/owner/spas/{spaId}/add-ons")
    @PreAuthorize("@staffAccess.desk(#spaId)")
    public List<BookingAddOns.AddOn> list(@PathVariable Long spaId) {
        return addOns.forSpa(spaId);
    }

    @PostMapping("/api/owner/spas/{spaId}/add-ons")
    @PreAuthorize("@staffAccess.spa(#spaId)")
    public BookingAddOns.AddOn create(@PathVariable Long spaId, @Valid @RequestBody AddOnInput input) {
        spas.findById(spaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spa not found"));
        return addOns.create(spaId, input.toInput());
    }

    @PutMapping("/api/owner/spas/{spaId}/add-ons/{id}")
    @PreAuthorize("@staffAccess.spa(#spaId)")
    public BookingAddOns.AddOn update(@PathVariable Long spaId, @PathVariable Long id, @Valid @RequestBody AddOnInput input) {
        return addOns.update(spaId, id, input.toInput());
    }

    @DeleteMapping("/api/owner/spas/{spaId}/add-ons/{id}")
    @PreAuthorize("@staffAccess.spa(#spaId)")
    public void delete(@PathVariable Long spaId, @PathVariable Long id) {
        addOns.delete(spaId, id);
    }

    // ---------------------------------------------------------------- booking flow

    /** The add-ons a guest can choose with this treatment, in menu order. Public, like the treatments. */
    @GetMapping("/api/spas/{spaId}/add-ons")
    public List<GuestAddOn> forService(@PathVariable Long spaId, @RequestParam Long serviceId) {
        var service = services.findByIdAndSpa_Id(serviceId, spaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment not found"));
        if (Boolean.FALSE.equals(service.getIsActive())) return List.of();
        return addOns.bookable(spaId, serviceId).stream().map(GuestAddOn::of).toList();
    }
}
