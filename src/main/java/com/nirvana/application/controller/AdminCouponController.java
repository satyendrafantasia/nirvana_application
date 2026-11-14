package com.nirvana.application.controller;

import com.nirvana.application.model.Coupon;
import com.nirvana.application.model.enums.DiscountType;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.repository.CouponRepository;
import com.nirvana.application.repository.SpaRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/** Platform administrators create and manage the discount codes customers can use at checkout. */
@RestController
@RequestMapping("/api/admin/coupons")
@PreAuthorize("@staffAccess.admin()")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponRepository coupons;
    private final BookingRepository bookings;
    private final SpaRepository spas;

    /**
     * percentOff is used for PERCENTAGE coupons and amountOffCents for FLAT ones.
     * A null limit means unlimited; a null spaId means the coupon works at every spa.
     */
    public record CouponInput(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "use letters, digits, - or _ only") String code,
            @NotNull DiscountType discountType,
            @Min(1) @Max(100) Integer percentOff,
            @Min(1) Integer amountOffCents,
            @Min(1) Integer maxDiscountCents,
            @Min(0) Integer minOrderCents,
            @NotNull OffsetDateTime validFrom,
            @NotNull OffsetDateTime validTo,
            @Min(1) Integer maxUsesPerUser,
            @Min(1) Integer maxUsesGlobal,
            Long spaId,
            Boolean active) {
    }

    public record CouponView(Long id, String code, DiscountType discountType, Integer percentOff, Integer amountOffCents,
                             Integer maxDiscountCents, Integer minOrderCents, OffsetDateTime validFrom, OffsetDateTime validTo,
                             Integer maxUsesPerUser, Integer maxUsesGlobal, Long spaId, boolean active, long timesUsed) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CouponView> list() {
        return coupons.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().map(this::view).toList();
    }

    @PostMapping
    @Transactional
    public CouponView create(@Valid @RequestBody CouponInput input) {
        String code = input.code().trim().toUpperCase();
        if (coupons.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A coupon with this code already exists.");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        apply(coupon, input);
        return view(coupons.save(coupon));
    }

    /** Changes the terms of a coupon. The code itself is fixed; set active=false to retire it. */
    @PutMapping("/{id}")
    @Transactional
    public CouponView update(@PathVariable Long id, @Valid @RequestBody CouponInput input) {
        Coupon coupon = coupons.findById(id).orElseThrow(() -> new EntityNotFoundException("Coupon not found"));
        if (!coupon.getCode().equalsIgnoreCase(input.code().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A coupon code cannot be changed. Create a new coupon instead.");
        }
        apply(coupon, input);
        return view(coupons.save(coupon));
    }

    private void apply(Coupon coupon, CouponInput input) {
        if (!input.validTo().isAfter(input.validFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validTo must be after validFrom.");
        }
        Integer value = input.discountType() == DiscountType.PERCENTAGE ? input.percentOff() : input.amountOffCents();
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, input.discountType() == DiscountType.PERCENTAGE
                    ? "percentOff is required for a percentage coupon."
                    : "amountOffCents is required for a flat coupon.");
        }
        coupon.setDiscountType(input.discountType());
        coupon.setDiscountValue(value);
        coupon.setMaxDiscountCents(input.maxDiscountCents());
        coupon.setMinOrderCents(input.minOrderCents());
        coupon.setValidFrom(input.validFrom());
        coupon.setValidTo(input.validTo());
        coupon.setMaxUsesPerUser(input.maxUsesPerUser());
        coupon.setMaxUsesGlobal(input.maxUsesGlobal());
        coupon.setActive(input.active() == null || input.active());
        coupon.setSpa(input.spaId() == null ? null
                : spas.findById(input.spaId()).orElseThrow(() -> new EntityNotFoundException("Spa not found: " + input.spaId())));
    }

    private CouponView view(Coupon c) {
        boolean percentage = c.getDiscountType() == DiscountType.PERCENTAGE;
        return new CouponView(c.getId(), c.getCode(), c.getDiscountType(),
                percentage ? c.getDiscountValue() : null, percentage ? null : c.getDiscountValue(),
                c.getMaxDiscountCents(), c.getMinOrderCents(), c.getValidFrom(), c.getValidTo(),
                c.getMaxUsesPerUser(), c.getMaxUsesGlobal(), c.getSpa() != null ? c.getSpa().getId() : null,
                c.isActive(), bookings.countCouponUses(c.getId()));
    }
}
