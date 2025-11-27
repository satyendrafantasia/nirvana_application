package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.spa.TherapistType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "SpaResponse", description = "Detailed representation of a spa after creation or update")
public class SpaResponseDTO {

    private Long id;
    private String name;
    private String description;

    private AddressDTO address;
    private String timezone;

    private String phone;
    private String email;
    private String websiteUrl;

    private String gstin;
    private String businessRegistrationNumber;
    private String ownerName;

    private Boolean active;
    private Boolean verified;
    private Boolean featured;

    private Float ratingAvg;
    private Integer ratingCount;
    private Long totalBookings;

    private String facebookUrl;
    private String instagramUrl;

    private String defaultCurrency;
    private Integer taxPercent;
    private Integer commissionPct;

    @Schema(description = "Images attached to the spa profile")
    private List<SpaMediaImageDto> images;

    @Schema(description = "Videos attached to the spa profile")
    private List<SpaMediaVideoDto> videos;

    @Schema(description = "Therapist identifiers linked to the spa")
    private List<Long> therapistIds;

    @Schema(description = "Therapist origin/type categories supported")
    private Set<TherapistType> therapistTypesAvailable;

    @Schema(description = "Service identifiers offered by the spa")
    private List<Long> serviceIds;

    @Schema(description = "Opening time in local timezone", example = "09:00:00")
    private String openTimeLocal;

    @Schema(description = "Closing time in local timezone", example = "21:00:00")
    private String closeTimeLocal;

    @Schema(description = "Working days for the spa", example = "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\"]")
    private List<DayOfWeek> workingDays;

    private Long version;
}
