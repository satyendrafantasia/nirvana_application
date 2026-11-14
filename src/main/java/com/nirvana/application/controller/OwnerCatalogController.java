package com.nirvana.application.controller;

import com.nirvana.application.model.Therapist;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.Service;
import com.nirvana.application.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api/owner/spas/{spaId}")
@RequiredArgsConstructor
@PreAuthorize("@staffAccess.spa(#spaId)")
@Transactional
public class OwnerCatalogController {
    private final SpaRepository spas;
    private final BookingRepository bookings;
    private final jakarta.persistence.EntityManager em;
    private void requireAvailableForRemoval(long id){
        var appointments=em.createQuery("select b.id from Booking b where b.therapist.id=:id and b.endTs>:now and b.status in ('ARRIVED','CONFIRMED','PENDING_PAYMENT')",Long.class)
            .setParameter("id",id).setParameter("now",java.time.OffsetDateTime.now()).getResultList();
        if(!appointments.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Resolve appointments "+appointments+" first");
        if(em.createQuery("select count(h) from BookingHold h where h.therapist.id=:id and h.convertedToBooking=false and h.expiresAt>:now",Long.class)
            .setParameter("id",id).setParameter("now",java.time.OffsetDateTime.now()).getSingleResult()>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Wait for the active therapist hold to expire");
    }
    @DeleteMapping("/therapists/{id}")
    public void retire(@PathVariable Long spaId,@PathVariable Long id){
        spas.lockSchedule(spaId).orElseThrow(this::missing);
        var therapist=therapists.findByIdAndSpa_Id(id,spaId).orElseThrow(this::missing);
        requireAvailableForRemoval(id);therapist.setIsActive(false);therapist.setIsAvailable(false);
    }
    private final TherapistRepository therapists;
    private final ServiceRepository services;
    public record TherapistInput(@NotBlank @Size(max=255) String name, @Size(max=128) String type,
        @Size(max=2000) String bio, @NotNull Boolean active, @NotNull Boolean available,
        @NotNull Set<@NotNull Long> serviceIds, Long version) {}
    public record TherapistView(Long id, String name, String type, String bio, Boolean active,
        Boolean available, Set<Long> serviceIds, Long version) {}
    public record ServiceInput(@NotBlank @Size(max=255) String name, @Size(max=2000) String description,
        @NotNull @Min(1) @Max(1440) Integer durationMin, @NotNull @Min(0) @Max(1440) Integer bufferMin,
        @NotNull @Min(0) Integer basePriceCents, @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
        @NotNull Boolean active) {}
    public record ServiceView(Long id, String name, String description, Integer durationMin,
        Integer bufferMin, Integer basePriceCents, String currency, Boolean active) {}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found in this spa");}
    private TherapistView view(Therapist t){
        Set<Long> ids=new HashSet<>();t.getServices().forEach(s->ids.add(s.getId()));
        if(t.getService()!=null)ids.add(t.getService().getId());
        return new TherapistView(t.getId(),t.getName(),t.getType(),t.getBio(),t.getIsActive(),t.getIsAvailable(),ids,t.getVersion());
    }
    private ServiceView view(Service s){return new ServiceView(s.getId(),s.getName(),s.getDescription(),s.getDurationMin(),s.getBufferMin(),s.getBasePriceCents(),s.getCurrency(),s.getIsActive());}
    @GetMapping("/therapists")
    public List<TherapistView> therapists(@PathVariable Long spaId){return therapists.findBySpa_IdOrderByNameAsc(spaId).stream().map(this::view).toList();}
    @PostMapping("/therapists")
    public TherapistView createTherapist(@PathVariable Long spaId,@Valid @RequestBody TherapistInput input){
        var t=new Therapist();t.setSpa(spas.findById(spaId).orElseThrow(this::missing));return save(t,input);
    }
    @PutMapping("/therapists/{id}")
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TherapistView updateTherapist(@PathVariable Long spaId,@PathVariable Long id,@Valid @RequestBody TherapistInput input){
        spas.lockSchedule(spaId).orElseThrow(this::missing);
        var t=therapists.findByIdAndSpa_Id(id,spaId).orElseThrow(this::missing);
        if(input.version()==null || !Objects.equals(input.version(),t.getVersion()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Therapist changed; reload before saving");
        if(!input.active() || !input.available())requireAvailableForRemoval(id);
        var required = bookings.committedServices(id, java.time.OffsetDateTime.now(), List.of(
                BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT, BookingStatus.ARRIVED));
        if (!input.serviceIds().containsAll(required)) {
            var affected=em.createQuery("select b from Booking b where b.therapist.id=:id and b.endTs>:now and b.status in ('ARRIVED','CONFIRMED','PENDING_PAYMENT')",com.nirvana.application.model.Booking.class)
                .setParameter("id",id).setParameter("now",java.time.OffsetDateTime.now()).getResultList().stream()
                .filter(b->!input.serviceIds().contains(b.getService().getId())).map(b->b.getId()).toList();
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Resolve appointments "+affected+" before removing these services");
        }
        return save(t,input);
    }
    private TherapistView save(Therapist t,TherapistInput in){
        var assigned=services.findAllById(in.serviceIds());
        if(assigned.size()!=in.serviceIds().size() || assigned.stream().anyMatch(s->!Objects.equals(s.getSpa().getId(),t.getSpa().getId())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Services must belong to this spa");
        t.setName(in.name().trim());t.setDisplayName(in.name().trim());t.setType(in.type());t.setBio(in.bio());
        t.setIsActive(in.active());t.setIsAvailable(in.available());t.setService(null);
        t.getServices().clear();t.getServices().addAll(assigned);
        return view(therapists.saveAndFlush(t));
    }
    @GetMapping("/services")
    public List<ServiceView> services(@PathVariable Long spaId){return services.findBySpa_IdOrderByNameAsc(spaId).stream().map(this::view).toList();}
    @PostMapping("/services")
    public ServiceView createService(@PathVariable Long spaId,@Valid @RequestBody ServiceInput input){
        var s=new Service();s.setSpa(spas.findById(spaId).orElseThrow(this::missing));return save(s,input);
    }
    @PutMapping("/services/{id}")
    public ServiceView updateService(@PathVariable Long spaId,@PathVariable Long id,@Valid @RequestBody ServiceInput input){return save(services.findByIdAndSpa_Id(id,spaId).orElseThrow(this::missing),input);}
    private ServiceView save(Service s,ServiceInput in){
        s.setName(in.name().trim());s.setDescription(in.description());s.setDurationMin(in.durationMin());s.setDurationMinutes(in.durationMin());
        s.setBufferMin(in.bufferMin());s.setBasePriceCents(in.basePriceCents());s.setPriceCents(in.basePriceCents());s.setCurrency(in.currency());s.setIsActive(in.active());
        return view(services.saveAndFlush(s));
    }
}
