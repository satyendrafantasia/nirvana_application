package com.nirvana.application.model.dto;

// src/main/java/com/nirvana/application/model/dto/SpaDetailResponse.java

import com.nirvana.application.model.enums.KycStatus;
import com.nirvana.application.model.enums.spa.TherapistType;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public record SpaDetailResponse(
        Long id,
        String name,
        String description,

        // Address
        String addressLine1,
        String addressLine2,
        String locality,
        String landmark,
        String city,
        String state,
        String postalCode,
        String country,
        String countryCode,
        String formattedAddress,
        String timezone,
        String googlePlaceId,
        Double latitude,
        Double longitude,

        // Contact / social
        String phone,
        String email,
        String websiteUrl,
        String facebookUrl,
        String instagramUrl,

        // Business & KYC
        String gstin,
        String businessRegistrationNumber,
        String ownerName,
        KycStatus kycStatus,
        OffsetDateTime kycRequestedAt,
        OffsetDateTime kycApprovedAt,
        OffsetDateTime kycRejectedAt,
        String kycRejectedReason,
        Boolean isActive,
        Boolean isVerified,
        Boolean isFeatured,

        // Metrics
        Float ratingAvg,
        Integer ratingCount,
        Long totalBookings,

        // Config & financials
        String defaultCurrency,
        Integer taxPercent,
        Integer commissionPct,
        Integer maxConcurrentServices,
        Integer maxAdvanceBookingDays,
        Integer minNoticeMinutes,

        // Operating hours & availability
        String openTimeLocal,
        String closeTimeLocal,
        List<DayOfWeek> workingDays,

        // Therapist preferences
        Boolean allowTherapistSelection,
        Boolean allowTherapistTypeSelection,
        Set<TherapistType> therapistTypesAvailable,
        List<Long> therapistIds,

        // Services
        List<Long> serviceIds,

        // Experience
        List<String> images,
        List<String> videos,
        List<String> amenities,
        List<String> tags,

        // Primary hero image
        String heroImageUrl
) {}
