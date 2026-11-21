package com.nirvana.application.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps spas out of Google results that only carry "spa" as a secondary type,
 * such as hair salons, gyms and hotels that also offer a spa service.
 */
public final class SpaPlaceFilter {
    private SpaPlaceFilter() {}

    /** Primary types we treat as a spa business. */
    static final Set<String> SPA_TYPES = Set.of("spa", "massage", "massage_spa", "day_spa", "sauna", "wellness_center");

    /** Primary types sent to Google as excludedPrimaryTypes on nearby searches. All are Places API (New) Table A types. */
    public static final List<String> EXCLUDED_PRIMARY_TYPES = List.of(
            "beauty_salon", "hair_salon", "hair_care", "nail_salon", "barber_shop", "beautician", "makeup_artist",
            "gym", "fitness_center", "hotel", "lodging");

    private static final Set<String> NON_SPA_TYPES = Set.of(
            "beauty_salon", "hair_salon", "hair_care", "nail_salon", "barber_shop", "beautician", "makeup_artist",
            "tanning_studio", "gym", "fitness_center", "sports_club", "sports_complex", "yoga_studio",
            "hotel", "lodging", "resort_hotel", "motel", "guest_house", "hostel", "bed_and_breakfast",
            "swimming_pool", "clothing_store", "store", "cosmetics_store");

    private static final Pattern NON_SPA_NAME = Pattern.compile(
            "\\b(salons?|saloons?|barbers?|barbershop|parlou?rs?|gym|gyms|gymnasium|fitness|crossfit|unisex|hair|nails?|makeover|academy|institute)\\b",
            Pattern.CASE_INSENSITIVE);

    /** True when a Google place is a spa. Places without type data are judged by name alone. */
    public static boolean isSpa(JsonNode place) {
        String primary = place.path("primaryType").asText("").toLowerCase(Locale.ROOT);
        if (SPA_TYPES.contains(primary)) return true;
        if (NON_SPA_TYPES.contains(primary)) return false;
        String name = place.path("displayName").path("text").asText("");
        return !NON_SPA_NAME.matcher(name).find();
    }
}
