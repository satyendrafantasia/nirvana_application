package com.nirvana.application.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record PlaceDiscoveryRequest(
    @NotNull Mode mode,
    @Size(max=100) String city,
    @Size(max=100) String locality,
    @NotBlank @Pattern(regexp="[A-Za-z]{2}") String countryCode,
    @Valid Bounds bounds,
    @DecimalMin("-90") @DecimalMax("90") Double latitude,
    @DecimalMin("-180") @DecimalMax("180") Double longitude,
    @Min(100) @Max(50000) Integer radiusMeters,
    Rank rank,
    @Size(max=4096) String pageToken
) {
    public enum Mode { TEXT, NEARBY }
    public enum Rank { DISTANCE, POPULARITY }
    public record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
    public record Bounds(@NotNull @Valid Point low, @NotNull @Valid Point high) {}
    @AssertTrue(message="Choose a city for text search; choose a resolved area for locality search; nearby search requires coordinates and radius and has no pages.")
    public boolean isSearchValid() {
        if (mode == Mode.TEXT) return city != null && !city.isBlank()
                && (locality == null || locality.isBlank() || bounds != null);
        return mode == Mode.NEARBY && latitude != null && longitude != null && radiusMeters != null
                && (pageToken == null || pageToken.isBlank());
    }
    @AssertTrue(message="Invalid geographic bounds.")
    public boolean isBoundsValid() {
        if (bounds == null || bounds.low() == null || bounds.high() == null
            || bounds.low().latitude() == null || bounds.high().latitude() == null) return true;
        return bounds.low().latitude() <= bounds.high().latitude();
    }
}
