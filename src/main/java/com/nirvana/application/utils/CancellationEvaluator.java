package com.nirvana.application.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.Booking;
import com.nirvana.application.model.Service;
import com.nirvana.application.model.UserMembership;
import com.nirvana.application.model.enums.BookingStatus;
import com.nirvana.application.model.enums.RefundRoute;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@UtilityClass
@Slf4j
public class CancellationEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int FALLBACK_CUTOFF_MINUTES = 120;

    public CancellationEligibility evaluateCancellation(Booking booking, OffsetDateTime now, int cutoffMinutes) {
        if (booking == null) {
            return CancellationEligibility.denied("Booking not found");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return CancellationEligibility.denied("Booking already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            return CancellationEligibility.denied("Service already completed");
        }
        if (booking.getStatus() == BookingStatus.NO_SHOW) {
            return CancellationEligibility.denied("Marked as no-show");
        }
        OffsetDateTime start = booking.getStartTs();
        OffsetDateTime end = booking.getEndTs();

        if (start == null || end == null) {
            return CancellationEligibility.denied("Booking time not available");
        }
        if (!end.isAfter(start)) {
            return CancellationEligibility.denied("Invalid booking duration");
        }

        if (!now.isBefore(end)) {
            return CancellationEligibility.denied("Service already completed");
        }
        if (!now.isBefore(start)) {
            return CancellationEligibility.denied("Service already started");
        }

        CancellationPolicy policy = resolvePolicy(booking.getService(), cutoffMinutes);
        long minutesUntilStart = Duration.between(now, start).toMinutes();

        Optional<PolicyRule> matchedRule = policy.rules().stream()
                .filter(rule -> rule.matches(booking.getSpa().getId(), membershipTier(booking.getUserMembership()), minutesUntilStart))
                .max(Comparator.comparingInt(PolicyRule::minAdvanceMinutes));

        if (matchedRule.isPresent()) {
            PolicyRule rule = matchedRule.get();
            if (minutesUntilStart < rule.minAdvanceMinutes()) {
                return CancellationEligibility.denied(rule.reason() != null
                        ? rule.reason()
                        : "Within provider cut-off window");
            }
            return CancellationEligibility.allowed(rule.feePercent(), rule.refundRoute(), rule.notifyBeforeMinutes() != null
                    ? start.minusMinutes(rule.notifyBeforeMinutes())
                    : null);
        }

        if (minutesUntilStart < policy.defaultCutoffMinutes()) {
            return CancellationEligibility.denied("Within " + policy.defaultCutoffMinutes() + "-minute cutoff window");
        }

        return CancellationEligibility.allowed(policy.defaultFeePercent(), policy.defaultRoute(), start.minusMinutes(policy.defaultCutoffMinutes()));
    }

    private String membershipTier(UserMembership membership) {
        if (membership == null || membership.getPlan() == null) {
            return null;
        }
        return membership.getPlan().getName();
    }

    private CancellationPolicy resolvePolicy(Service service, int cutoffMinutes) {
        if (service == null || service.getCancellationPolicyJson() == null) {
            return CancellationPolicy.defaultPolicy(cutoffMinutes);
        }
        try {
            Map<String, Object> map = MAPPER.readValue(service.getCancellationPolicyJson(), new TypeReference<>() {});
            int defaultCutoff = ((Number) map.getOrDefault("defaultCutoffMinutes", cutoffMinutes)).intValue();
            int fee = ((Number) map.getOrDefault("defaultFeePercent", 0)).intValue();
            RefundRoute route = RefundRoute.valueOf(
                    String.valueOf(map.getOrDefault("defaultRoute", RefundRoute.ORIGINAL_METHOD.name())).toUpperCase());
            List<PolicyRule> rules = ((List<Map<String, Object>>) map.getOrDefault("rules", List.of()))
                    .stream()
                    .map(PolicyRule::fromMap)
                    .toList();
            return new CancellationPolicy(defaultCutoff, fee, route, rules);
        } catch (Exception e) {
            log.warn("Falling back to default cancellation policy due to parse error: {}", e.getMessage());
            return CancellationPolicy.defaultPolicy(Math.max(cutoffMinutes, FALLBACK_CUTOFF_MINUTES));
        }
    }

    private record CancellationPolicy(int defaultCutoffMinutes, int defaultFeePercent, RefundRoute defaultRoute, List<PolicyRule> rules) {
        private static CancellationPolicy defaultPolicy(int cutoffMinutes) {
            return new CancellationPolicy(cutoffMinutes, 0, RefundRoute.ORIGINAL_METHOD, List.of());
        }
    }

    private record PolicyRule(Long providerId, String membershipTier, int minAdvanceMinutes, int feePercent,
                              RefundRoute refundRoute, String reason, Integer notifyBeforeMinutes) {
        private boolean matches(Long spaId, String userMembershipTier, long minutesUntilStart) {
            boolean providerMatch = providerId == null || providerId.equals(spaId);
            boolean membershipMatch = membershipTier == null
                    || (userMembershipTier != null && userMembershipTier.equalsIgnoreCase(membershipTier));
            return providerMatch && membershipMatch && minutesUntilStart >= minAdvanceMinutes;
        }

        @SuppressWarnings("unchecked")
        private static PolicyRule fromMap(Map<String, Object> map) {
            Long provider = map.get("providerId") != null ? Long.valueOf(String.valueOf(map.get("providerId"))) : null;
            String membership = map.get("membershipTier") != null ? String.valueOf(map.get("membershipTier")) : null;
            int minAdvance = ((Number) map.getOrDefault("minAdvanceMinutes", FALLBACK_CUTOFF_MINUTES)).intValue();
            int fee = ((Number) map.getOrDefault("feePercent", 0)).intValue();
            RefundRoute route = RefundRoute.valueOf(
                    String.valueOf(map.getOrDefault("refundRoute", RefundRoute.ORIGINAL_METHOD.name())).toUpperCase());
            String reason = map.get("reason") != null ? String.valueOf(map.get("reason")) : null;
            Integer notifyBefore = map.get("notifyBeforeMinutes") != null
                    ? Integer.valueOf(String.valueOf(map.get("notifyBeforeMinutes")))
                    : null;
            return new PolicyRule(provider, membership, minAdvance, fee, route, reason, notifyBefore);
        }
    }
}
