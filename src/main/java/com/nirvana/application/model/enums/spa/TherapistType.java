package com.nirvana.application.model.enums.spa;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Therapist origin/type categories")
public enum TherapistType {
    INDIAN,
    THAI,
    NORTH_EAST,
    OTHER
}
