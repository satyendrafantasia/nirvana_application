package com.nirvana.application.service.impl;

import com.nirvana.application.model.MediaAsset;
import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.PagedResponse;
import com.nirvana.application.model.dto.SpaSummaryResponse;
import com.nirvana.application.model.enums.MediaType;
import com.nirvana.application.repository.MediaAssetRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.ServiceRepository;
import com.nirvana.application.repository.projection.SpaDistanceProjection;
import com.nirvana.application.repository.projection.SpaStartingPriceProjection;
import com.nirvana.application.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.nirvana.application.repository.spec.SpaAddressSpecifications.*;
import static com.nirvana.application.repository.spec.SpaSpecifications.*;

/**
 * Unified Spa search service.
 *
 * Supports:
 *  - city / country / locality filters (via embedded Address)
 *  - free text (name, locality, address, tags)
 *  - min rating
 *  - service-based search (id, name, category)
 *  - active + verified + KYC filtering
 *  - pagination
 *  - sorting (featured, rating, name, distance)
 *  - thumbnail via MediaAsset
 *
 * Distance sort uses native SQL Haversine (SpaRepository) when:
 *  sort = "distance" AND centerLat/centerLon/radiusKm are provided.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SpaSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SpaRepository spaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ServiceRepository serviceRepository;
    private final S3Service s3Service;

    /**
     * Main search method to be wired to /api/spas.
     *
     * @param city             filter by city (optional)
     * @param countryCode      filter by countryCode (optional)
     * @param localityLike     filter by locality LIKE (optional)
     * @param freeText         text search on spa name/address/tags (optional)
     * @param minLat           manual geo bounding box minLat (optional)
     * @param maxLat           manual geo bounding box maxLat (optional)
     * @param minLon           manual geo bounding box minLon (optional)
     * @param maxLon           manual geo bounding box maxLon (optional)
     * @param minRating        minimum rating filter (optional)
     * @param serviceId        filter spas that offer this service id (optional)
     * @param serviceName      filter spas that offer service with this name (optional)
     * @param serviceCategory  filter spas that offer service in this category (optional)
     * @param centerLat        center latitude for geo distance sort (optional)
     * @param centerLon        center longitude for geo distance sort (optional)
     * @param radiusKm         radius in km for geo distance sort (optional)
     * @param sort             sort key: featured|rating_asc|rating_desc|name_asc|distance
     * @param page             page index (0-based)
     * @param size             page size
     */
    public PagedResponse<SpaSummaryResponse> searchSpas(
            String city,
            String countryCode,
            String localityLike,
            String freeText,
            Double minLat,
            Double maxLat,
            Double minLon,
            Double maxLon,
            Float minRating,
            Long serviceId,
            String serviceName,
            String serviceCategory,
            Double centerLat,
            Double centerLon,
            Double radiusKm,
            String sort,
            Integer page,
            Integer size
    ) {
        int pageNumber = (page == null || page < 0) ? 0 : page;
        int pageSize = (size == null || size <= 0) ? 10 : Math.min(size, MAX_PAGE_SIZE);

        // base spec (true) + global business rules
        Specification<Spa> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and(isActiveAndVerifiedAndKycOk());

        // address-based filters (using embedded Address)
        if (StringUtils.hasText(city)) {
            spec = spec.and(cityEquals(city.trim()));
        }
        if (StringUtils.hasText(countryCode)) {
            spec = spec.and(countryCodeEquals(countryCode.trim()));
        }
        if (StringUtils.hasText(localityLike)) {
            spec = spec.and(localityLike(localityLike.trim()));
        }
        if (StringUtils.hasText(freeText)) {
            spec = spec.and(freeText(freeText.trim()));
        }

        // manual bounding box filters (if provided explicitly)
        spec = spec.and(withinLatLonBox(minLat, maxLat, minLon, maxLon));

        // rating
        spec = spec.and(ratingAtLeast(minRating));

        // service-based filters
        spec = spec.and(hasServiceId(serviceId));
        spec = spec.and(hasServiceNameLike(serviceName));
        spec = spec.and(hasServiceCategory(serviceCategory));

        boolean distanceSort =
                "distance".equalsIgnoreCase(sort)
                        && centerLat != null
                        && centerLon != null
                        && radiusKm != null
                        && radiusKm > 0;

        if (distanceSort) {
            // Use native geo search + then enrich with Spa & thumbnail
            return searchByGeoDistance(
                    spec,
                    centerLat,
                    centerLon,
                    radiusKm,
                    pageNumber,
                    pageSize
            );
        } else {
            // Normal spec-based search with DB sorting
            Sort springSort = buildSort(sort);
            Pageable pageable = PageRequest.of(pageNumber, pageSize, springSort);

            Page<Spa> pageResult = spaRepository.findAll(spec, pageable);

            Map<Long, Integer> startingPrices = fetchStartingPrices(
                    pageResult.getContent().stream().map(Spa::getId).toList()
            );

            List<SpaSummaryResponse> content = pageResult.getContent()
                    .stream()
                    .map(spa -> toSummary(spa, null, startingPrices.get(spa.getId())))
                    .toList();

            return new PagedResponse<>(
                    content,
                    pageResult.getNumber(),
                    pageResult.getSize(),
                    pageResult.getTotalElements(),
                    pageResult.getTotalPages()
            );
        }
    }

    /**
     * Geo-distance mode using native Haversine query.
     * NOTE:
     *  - Applies basic active/kyc/rating filters via spec BEFORE calling this,
     *    by constructing bounding box manually here.
     *  - Service-based filters are NOT yet pushed into the native query; if you
     *    need that, extend the query with JOINs on service.
     */
    private PagedResponse<SpaSummaryResponse> searchByGeoDistance(
            Specification<Spa> baseSpec,
            double centerLat,
            double centerLon,
            double radiusKm,
            int pageNumber,
            int pageSize
    ) {
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("radiusKm must be > 0 when sorting by distance");
        }

        // Rough conversion: 1 degree latitude ≈ 111 km
        double latRadius = radiusKm / 111.0;
        double minLat = centerLat - latRadius;
        double maxLat = centerLat + latRadius;

        double lonRadius = radiusKm / (111.0 * Math.cos(Math.toRadians(centerLat)));
        double minLon = centerLon - lonRadius;
        double maxLon = centerLon + lonRadius;

        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // Native query: accurate Haversine distance + paging
        Page<SpaDistanceProjection> geoPage =
                spaRepository.findSpasWithinRadiusKmAndBoxPaged(
                        centerLat, centerLon, radiusKm,
                        minLat, maxLat, minLon, maxLon,
                        pageable
                );

        if (geoPage.isEmpty()) {
            return new PagedResponse<>(
                    List.of(),
                    geoPage.getNumber(),
                    geoPage.getSize(),
                    0L,
                    0
            );
        }

        // Collect ids in order of distance
        List<Long> spaIdsInOrder = geoPage.getContent().stream()
                .map(SpaDistanceProjection::getId)
                .toList();

        // Fetch full Spa entities in a single query, constrained by ids
        Specification<Spa> specWithIds = baseSpec.and(
                (root, query, cb) -> root.get("id").in(spaIdsInOrder)
        );

        List<Spa> spas = spaRepository.findAll(specWithIds);

        Map<Long, Integer> startingPrices = fetchStartingPrices(spaIdsInOrder);

        // index by id, then re-map in projection order
        var spaById = spas.stream()
                .collect(java.util.stream.Collectors.toMap(Spa::getId, s -> s));

        List<SpaSummaryResponse> result = new ArrayList<>();

        for (SpaDistanceProjection proj : geoPage.getContent()) {
            Spa spa = spaById.get(proj.getId());
            if (spa == null) {
                // filtered out by spec (e.g., inactive/KYC/rating).
                continue;
            }
            result.add(toSummary(spa, proj.getDistanceKm(), startingPrices.get(spa.getId())));
        }

        // NOTE:
        // totalElements/totalPages come from the geo query,
        // which does not know about all spec filters.
        // For strict correctness you would push those filters into the SQL.
        long totalElements = geoPage.getTotalElements();
        int totalPages = geoPage.getTotalPages();

        return new PagedResponse<>(
                result,
                geoPage.getNumber(),
                geoPage.getSize(),
                totalElements,
                totalPages
        );
    }

    private Sort buildSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(
                    Sort.Order.desc("isFeatured"),
                    Sort.Order.desc("ratingAvg"),
                    Sort.Order.desc("ratingCount"),
                    Sort.Order.asc("name")
            );
        }

        return switch (sort.toLowerCase()) {
            case "rating_asc" -> Sort.by(Sort.Order.asc("ratingAvg"));
            case "rating_desc" -> Sort.by(Sort.Order.desc("ratingAvg"));
            case "featured" -> Sort.by(
                    Sort.Order.desc("isFeatured"),
                    Sort.Order.desc("ratingAvg"),
                    Sort.Order.desc("ratingCount")
            );
            case "name_asc" -> Sort.by(Sort.Order.asc("name"));
            case "distance" ->
                // actual distance sort handled in searchByGeoDistance; DB sort here is unused
                    Sort.unsorted();
            default -> Sort.by(
                    Sort.Order.desc("isFeatured"),
                    Sort.Order.desc("ratingAvg"),
                    Sort.Order.desc("ratingCount"),
                    Sort.Order.asc("name")
            );
        };
    }

    private Map<Long, Integer> fetchStartingPrices(List<Long> spaIds) {
        if (spaIds == null || spaIds.isEmpty()) {
            return Map.of();
        }

        return serviceRepository.findStartingPricesBySpaIds(spaIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SpaStartingPriceProjection::getSpaId,
                        SpaStartingPriceProjection::getStartingPriceCents
                ));
    }

    private SpaSummaryResponse toSummary(Spa spa, Double distanceKm, Integer startingPriceCents) {
        var thumbnailAsset = mediaAssetRepository
                .findFirstBySpaIdAndMediaTypeOrderByPositionAscIdAsc(spa.getId(), MediaType.IMAGE);

        String thumbnail = null;
        if (thumbnailAsset.isPresent()) {
            thumbnail = s3Service.getFileUrl(thumbnailAsset.get().getObjectKey());
        }

        // Assuming Spa has embedded Address: spa.getAddress().getCity(), etc.
        String city = spa.getAddress() != null ? spa.getAddress().getCity() : null;
        String state = spa.getAddress() != null ? spa.getAddress().getState() : null;

        return new SpaSummaryResponse(
                spa.getId(),
                spa.getName(),
                city,
                state,
                spa.getRatingAvg(),
                spa.getRatingCount(),
                thumbnail,
                startingPriceCents,
                distanceKm
        );
    }
}
