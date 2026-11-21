package com.nirvana.application.repository.spec;

import com.nirvana.application.model.Spa;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class SpaAddressSpecifications {

    private SpaAddressSpecifications() {}

    /** Cities known by two names; Google Maps and guests use either. */
    private static final java.util.List<java.util.Set<String>> CITY_NAMES = java.util.List.of(
            java.util.Set.of("gurgaon", "gurugram"), java.util.Set.of("delhi", "new delhi"),
            java.util.Set.of("bangalore", "bengaluru"),
            java.util.Set.of("bombay", "mumbai"), java.util.Set.of("calcutta", "kolkata"),
            java.util.Set.of("madras", "chennai"), java.util.Set.of("poona", "pune"),
            java.util.Set.of("mysore", "mysuru"), java.util.Set.of("trivandrum", "thiruvananthapuram"));

    public static java.util.Set<String> cityNames(String city) {
        String name = city.strip().toLowerCase(java.util.Locale.ROOT);
        return CITY_NAMES.stream().filter(names -> names.contains(name)).findFirst().orElse(java.util.Set.of(name));
    }

    public static Specification<Spa> cityEquals(String city) {
        return (root, query, cb) -> {
            if (!has(city)) return null;
            return cb.lower(root.get("address").get("city")).in(cityNames(city));
        };
    }

    public static Specification<Spa> localityLike(String locality) {
        return (root, query, cb) -> {
            if (!has(locality)) return null;
            return cb.like(
                    cb.lower(root.get("address").get("locality")),
                    "%" + locality.toLowerCase() + "%"
            );
        };
    }

    public static Specification<Spa> countryCodeEquals(String code) {
        return (root, query, cb) -> {
            if (!has(code)) return null;
            return cb.equal(cb.lower(root.get("address").get("countryCode")), code.toLowerCase());
        };
    }

    public static Specification<Spa> addressContains(String text) {
        return (root, query, cb) -> {
            if (!has(text)) return null;

            String like = "%" + text.toLowerCase() + "%";
            var a = root.get("address");

            return cb.or(
                    cb.like(cb.lower(a.get("addressLine")), like),
                    cb.like(cb.lower(a.get("addressLine2")), like),
                    cb.like(cb.lower(a.get("locality")), like),
                    cb.like(cb.lower(a.get("landmark")), like),
                    cb.like(cb.lower(a.get("city")), like),
                    cb.like(cb.lower(a.get("state")), like),
                    cb.like(cb.lower(a.get("postalCode")), like),
                    cb.like(cb.lower(a.get("country")), like)
            );
        };
    }

    /**
     * Geo bounding — BigDecimal support
     */
    public static Specification<Spa> withinLatLonBox(
            Double minLat, Double maxLat,
            Double minLon, Double maxLon
    ) {
        return (root, query, cb) -> {
            var a = root.get("address");
            var predicate = cb.conjunction();

            if (minLat != null)
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(a.get("latitude"), BigDecimal.valueOf(minLat)));

            if (maxLat != null)
                predicate = cb.and(predicate,
                        cb.lessThanOrEqualTo(a.get("latitude"), BigDecimal.valueOf(maxLat)));

            if (minLon != null)
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(a.get("longitude"), BigDecimal.valueOf(minLon)));

            if (maxLon != null)
                predicate = cb.and(predicate,
                        cb.lessThanOrEqualTo(a.get("longitude"), BigDecimal.valueOf(maxLon)));

            return predicate.getExpressions().isEmpty() ? null : predicate;
        };
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
