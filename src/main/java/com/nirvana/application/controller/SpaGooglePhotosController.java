package com.nirvana.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nirvana.application.service.CityPlacesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Google Maps photos of a published spa that has none of its own, fetched live each time and
 * never stored, as Google's terms require. Open to guests, so the Place ID always comes from the
 * spa record, never the caller: this can't be used to fetch photos of any other place.
 */
@RestController
@RequestMapping("/api/spas/{spaId}/google-photos")
@RequiredArgsConstructor
public class SpaGooglePhotosController {
    private final CityPlacesClient places;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<JsonNode> photos(@PathVariable long spaId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places.photos(placeId(spaId)));
    }

    /** One photo from the list above, as an image. */
    @GetMapping("/{photoId}/image")
    public ResponseEntity<byte[]> image(@PathVariable long spaId, @PathVariable String photoId) {
        return places.photoImage(placeId(spaId), photoId);
    }

    private String placeId(long spaId) {
        var ids = jdbc.queryForList("SELECT google_place_id FROM spa WHERE id=? AND is_active=1 AND is_verified=1 AND google_place_id IS NOT NULL",
                String.class, spaId);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No Google photos for this spa.");
        return ids.get(0);
    }
}
