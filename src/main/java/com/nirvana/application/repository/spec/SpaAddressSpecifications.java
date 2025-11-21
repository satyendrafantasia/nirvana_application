package com.nirvana.application.repository.spec;

import com.nirvana.application.model.Spa;
import org.springframework.data.jpa.domain.Specification;

public final class SpaAddressSpecifications {

    private SpaAddressSpecifications() {
    }

    public static Specification<Spa> cityEquals(String city) {
        return (root, query, cb) -> {
            if (city == null || city.isBlank()) return null;
            return cb.equal(cb.lower(root.get("address").get("city")), city.toLowerCase());
        };
    }

    public static Specification<Spa> cityLike(String cityLike) {
        return (root, query, cb) -> {
            if (cityLike == null || cityLike.isBlank()) return null;
            String pattern = "%" + cityLike.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("address").get("city")), pattern);
        };
    }

    public static Specification<Spa> countryCodeEquals(String countryCode) {
        return (root, query, cb) -> {
            if (countryCode == null || countryCode.isBlank()) return null;
            return cb.equal(cb.lower(root.get("address").get("countryCode")), countryCode.toLowerCase());
        };
    }

    public static Specification<Spa> postalCodeEquals(String postalCode) {
        return (root, query, cb) -> {
            if (postalCode == null || postalCode.isBlank()) return null;
            return cb.equal(cb.lower(root.get("address").get("postalCode")), postalCode.toLowerCase());
        };
    }

    public static Specification<Spa> localityLike(String localityLike) {
        return (root, query, cb) -> {
            if (localityLike == null || localityLike.isBlank()) return null;
            String pattern = "%" + localityLike.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("address").get("locality")), pattern);
        };
    }

    public static Specification<Spa> stateEquals(String state) {
        return (root, query, cb) -> {
            if (state == null || state.isBlank()) return null;
            return cb.equal(cb.lower(root.get("address").get("state")), state.toLowerCase());
        };
    }

    public static Specification<Spa> countryEquals(String country) {
        return (root, query, cb) -> {
            if (country == null || country.isBlank()) return null;
            return cb.equal(cb.lower(root.get("address").get("country")), country.toLowerCase());
        };
    }

    public static Specification<Spa> addressContains(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) return null;
            String pattern = "%" + text.toLowerCase() + "%";

            var addressPath = root.get("address");

            return cb.or(
                    cb.like(cb.lower(addressPath.get("addressLine")), pattern),
                    cb.like(cb.lower(addressPath.get("addressLine2")), pattern),
                    cb.like(cb.lower(addressPath.get("locality")), pattern),
                    cb.like(cb.lower(addressPath.get("landmark")), pattern),
                    cb.like(cb.lower(addressPath.get("city")), pattern),
                    cb.like(cb.lower(addressPath.get("state")), pattern),
                    cb.like(cb.lower(addressPath.get("postalCode")), pattern),
                    cb.like(cb.lower(addressPath.get("country")), pattern)
            );
        };
    }

    /**
     * Filter by latitude/longitude bounding box (simple geo range).
     * Use this before you go for proper geo-indexing.
     * Every method returns null if the param is blank ⇒ Spring Data JPA ignores that spec, making chaining easy.

     * addressContains is your generic “search by text” across multiple address fields.

     * withinLatLonBox is a poor man’s geo search (good enough until you go to PostGIS / Elastic).
     */
    public static Specification<Spa> withinLatLonBox(Double minLat, Double maxLat,
                                                     Double minLon, Double maxLon) {
        return (root, query, cb) -> {
            var addressPath = root.get("address");

            var predicates = cb.conjunction();

            if (minLat != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(addressPath.get("latitude"), minLat));
            }
            if (maxLat != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(addressPath.get("latitude"), maxLat));
            }
            if (minLon != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(addressPath.get("longitude"), minLon));
            }
            if (maxLon != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(addressPath.get("longitude"), maxLon));
            }

            return predicates.getExpressions().isEmpty() ? null : predicates;
        };
    }
}
