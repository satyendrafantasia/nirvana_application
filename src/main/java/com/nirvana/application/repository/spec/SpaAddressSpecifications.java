package com.nirvana.application.repository.spec;

import com.nirvana.application.model.Spa;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class SpaAddressSpecifications {

    private SpaAddressSpecifications() {}

    public static Specification<Spa> cityEquals(String city) {
        return (root, query, cb) -> {
            if (!has(city)) return null;
            return cb.equal(cb.lower(root.get("address").get("city")), city.toLowerCase());
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
