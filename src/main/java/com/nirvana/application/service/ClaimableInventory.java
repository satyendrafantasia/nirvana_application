package com.nirvana.application.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ClaimableInventory {
    private final JdbcTemplate jdbc;

    static final String UNASSIGNED_IMPORT = """
            s.spa_manager_id IS NULL
            AND JSON_EXTRACT(s.meta,'$.onboardedFromLead') IS NOT NULL
            AND EXISTS (SELECT 1 FROM spa_lead l WHERE l.spa_id=s.id AND l.status='ONBOARDED')
            """;

    record Listing(long spaId, boolean claimable) {}

    Map<String, Listing> lookup(List<String> placeIds) {
        if (placeIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(placeIds.size(), "?"));
        return jdbc.query("""
                SELECT s.google_place_id, s.id, (""" + UNASSIGNED_IMPORT + """
                   AND NOT EXISTS (SELECT 1 FROM spa_listing_claim c WHERE c.spa_id=s.id AND c.status IN ('PENDING','APPROVED'))
                ) AS claimable
                FROM spa s WHERE s.google_place_id IN (""" + placeholders + ")", rs -> {
            Map<String, Listing> result = new HashMap<>();
            while (rs.next()) result.put(rs.getString(1), new Listing(rs.getLong(2), rs.getBoolean(3)));
            return result;
        }, placeIds.toArray());
    }

    Listing available(String placeId) {
        var listing = lookup(List.of(placeId)).get(placeId);
        if (listing != null && !listing.claimable()) throw ListingClaimService.alreadyListed();
        return listing;
    }
}
