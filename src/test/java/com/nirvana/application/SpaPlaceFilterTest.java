package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.service.SpaPlaceFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpaPlaceFilterTest {
    private final ObjectMapper json = new ObjectMapper();

    private boolean spa(String primaryType, String name) throws Exception {
        var place = json.createObjectNode().put("id", "p1");
        if (primaryType != null) place.put("primaryType", primaryType);
        place.putObject("displayName").put("text", name);
        return SpaPlaceFilter.isSpa(place);
    }

    @Test
    void keepsSpasAndMassageCentres() throws Exception {
        assertTrue(spa("spa", "Lotus Day Spa"));
        assertTrue(spa("massage", "Thai Touch"));
        assertTrue(spa("wellness_center", "Ayur Wellness"));
        // A spa's own type wins over a salon-like word in its name.
        assertTrue(spa("spa", "Serenity Spa & Salon"));
        assertTrue(spa(null, "Blue Lagoon Spa"));
    }

    @Test
    void dropsSalonsGymsAndHotels() throws Exception {
        assertFalse(spa("hair_salon", "Glow Spa"));
        assertFalse(spa("beauty_salon", "Looks"));
        assertFalse(spa("gym", "Iron Paradise"));
        assertFalse(spa("fitness_center", "Cult"));
        assertFalse(spa("hotel", "Grand Palace"));
        assertFalse(spa("barber_shop", "The Barber Spa"));
    }

    @Test
    void judgesUnknownTypesByName() throws Exception {
        assertFalse(spa(null, "Naturals Unisex Salon"));
        assertFalse(spa("point_of_interest", "Gold's Gym"));
        assertFalse(spa(null, "Jawed Habib Hair & Beauty"));
        assertFalse(spa(null, "Glam Beauty Parlour"));
        assertTrue(spa("point_of_interest", "Om Ayurveda Centre"));
    }
}
