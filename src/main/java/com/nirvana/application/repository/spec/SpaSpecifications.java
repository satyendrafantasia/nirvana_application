package com.nirvana.application.repository.spec;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.enums.KycStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class SpaSpecifications {

    private SpaSpecifications() {}

    public static Specification<Spa> isActiveAndVerifiedAndKycOk() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("isActive")),
                cb.isTrue(root.get("isVerified")),
                cb.equal(root.get("kycStatus"), KycStatus.APPROVED)
        );
    }

    public static Specification<Spa> ratingAtLeast(Float minRating) {
        return (root, query, cb) -> {
            if (minRating == null) return null;
            return cb.greaterThanOrEqualTo(root.get("ratingAvg"), minRating);
        };
    }

    public static Specification<Spa> hasServiceId(Long serviceId) {
        return (root, query, cb) -> {
            if (serviceId == null) return null;
            return cb.isMember(serviceId, root.get("servicesOfferedIds"));
        };
    }

    public static Specification<Spa> hasServiceNameLike(String name) {
        if (!StringUtils.hasText(name)) return null;

        return (root, query, cb) -> {
            Join<Object, Object> join = root.join("services", JoinType.LEFT);
            return cb.like(cb.lower(join.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<Spa> hasServiceCategory(String category) {
        if (!StringUtils.hasText(category)) return null;

        return (root, query, cb) -> {
            Join<Object, Object> join = root.join("services", JoinType.LEFT);
            return cb.equal(cb.lower(join.get("category")), category.toLowerCase());
        };
    }

    public static Specification<Spa> freeText(String queryText) {
        if (!StringUtils.hasText(queryText)) return null;
        String like = "%" + queryText.toLowerCase() + "%";

        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("description")), like),
                cb.like(cb.lower(root.get("tags").as(String.class)), like),
                // address fields fallback (optional – your Address spec handles most)
                cb.like(cb.lower(root.get("address").get("city")), like),
                cb.like(cb.lower(root.get("address").get("locality")), like)
        );
    }
}
