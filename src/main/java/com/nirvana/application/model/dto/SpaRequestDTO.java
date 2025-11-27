package com.nirvana.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import com.nirvana.application.model.enums.spa.TherapistType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "SpaRequest", description = "Spa profile payload used for onboarding and updates.")
public class SpaRequestDTO {

    @NotBlank
    @Schema(description = "Spa display name", example = "Nirvana Koramangala")
    private String name;

    @Schema(description = "Description that appears in discovery pages", example = "Boutique spa with steam and sauna")
    private String description;

    @NotNull
    @Valid
    @Schema(description = "Physical address for the spa")
    private AddressDTO address;

    @NotBlank
    @Schema(description = "IANA timezone for the spa", example = "Asia/Kolkata")
    private String timezone;

    @Schema(description = "Primary phone number", example = "+91-9999999999")
    private String phone;
    @Schema(description = "Contact email", example = "support@nirvana.test")
    private String email;
    @Schema(description = "Website URL", example = "https://nirvana.test/spa")
    private String websiteUrl;

    @Schema(description = "Opening time in spa's local timezone", example = "09:00:00")
    @NotBlank
    @Pattern(regexp = "^\\d{2}:\\d{2}:\\d{2}$", message = "openTimeLocal must be in HH:mm:ss format")
    private String openTimeLocal;

    @Schema(description = "Closing time in spa's local timezone", example = "21:00:00")
    @NotBlank
    @Pattern(regexp = "^\\d{2}:\\d{2}:\\d{2}$", message = "closeTimeLocal must be in HH:mm:ss format")
    private String closeTimeLocal;

    @Schema(description = "Working days for the spa", example = "[\"MONDAY\", \"TUESDAY\", \"WEDNESDAY\", \"THURSDAY\", \"FRIDAY\", \"SATURDAY\"]")
    private List<DayOfWeek> workingDays;

    @Schema(description = "Therapist types available at the spa", example = "[\"INDIAN\", \"THAI\"]")
    private Set<TherapistType> therapistTypesAvailable;

    @Schema(description = "Therapist identifiers to associate with the spa", example = "[2001, 2002]")
    private List<Long> therapistIds;

    @Schema(description = "Service identifiers offered at the spa", example = "[3001, 3002, 3003]")
    private List<Long> serviceIds;

    @Schema(description = "Uploaded image metadata for the spa")
    private List<SpaMediaImageDto> images;

    @Schema(description = "Uploaded video metadata for the spa")
    private List<SpaMediaVideoDto> videos;

    @Schema(description = "GSTIN for taxation", example = "29ABCDE1234F1Z5")
    private String gstin;
    @Schema(description = "Business registration number", example = "BRN-12345")
    private String businessRegistrationNumber;
    @Schema(description = "Owner or manager name", example = "Saanvi Rao")
    private String ownerName;

    @Schema(description = "Whether spa is active for booking", example = "true")
    private Boolean active;
    @Schema(description = "Whether spa should be highlighted in discovery", example = "false")
    private Boolean featured;

    @Schema(description = "Facebook profile URL")
    private String facebookUrl;
    @Schema(description = "Instagram profile URL")
    private String instagramUrl;

    @Schema(description = "Default currency code for payouts", example = "INR")
    private String defaultCurrency;
    @Schema(description = "Tax percentage applied to services", example = "18")
    private Integer taxPercent;
    @Schema(description = "Commission percentage for platform", example = "12")
    private Integer commissionPct;

    // required for updates (optimistic locking)
    @Schema(description = "Version for optimistic locking during updates")
    private Long version;
}
