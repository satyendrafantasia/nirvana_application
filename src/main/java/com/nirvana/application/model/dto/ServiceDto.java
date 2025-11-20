package com.nirvana.application.model.dto;

import com.nirvana.application.model.Service;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDto {
    private Long id;
    private String name;
    private String description;
    private Integer priceCents;
    private Integer durationMinutes;
    private Set<Long> therapistIds;
    private Boolean isActive;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ServiceDto fromEntity(Service svc) {
        if (svc == null) return null;
        return ServiceDto.builder()
                .id(svc.getId())
                .name(svc.getName())
                .description(svc.getDescription())
                .priceCents(svc.getPriceCents())
                .durationMinutes(svc.getDurationMinutes())
                .therapistIds(svc.getTherapists() != null ? svc.getTherapists().stream().map(t -> t.getId()).collect(java.util.stream.Collectors.toSet()) : null)
                .isActive(svc.getIsActive())
                .version(svc.getVersion())
                .createdAt(svc.getCreatedAt())
                .updatedAt(svc.getUpdatedAt())
                .build();
    }

    public Service toEntity() {
        Service s = Service.builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .priceCents(this.priceCents)
                .durationMinutes(this.durationMinutes)
                .isActive(this.isActive != null ? this.isActive : true)
                .version(this.version)
                .build();
        // note: therapist relation must be attached by caller using therapistIds
        return s;
    }
}

