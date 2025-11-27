package com.nirvana.application.service;

import com.nirvana.application.config.OperationsSlaProperties;
import com.nirvana.application.model.Closure;
import com.nirvana.application.model.dto.OperationsDashboardResponse;
import com.nirvana.application.model.enums.KycStatus;
import com.nirvana.application.model.enums.NotificationStatus;
import com.nirvana.application.repository.ClosureRepository;
import com.nirvana.application.repository.MediaAssetRepository;
import com.nirvana.application.repository.NotificationLogRepository;
import com.nirvana.application.repository.PaymentRepository;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.repository.UserRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationsObservabilityService {

    private final ClosureRepository closureRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final SpaRepository spaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OperationsSlaProperties slaProperties;
    private final AuditTrailService auditTrailService;

    @Observed(name = "operations.dashboard.snapshot")
    public OperationsDashboardResponse snapshot() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OperationsDashboardResponse.SlaReport availability = buildAvailabilitySla(now);
        OperationsDashboardResponse.SlaReport notification = buildNotificationSla(now);
        OperationsDashboardResponse.ComplianceSummary compliance = buildComplianceSummary();
        OperationsDashboardResponse.PayoutSummary payouts = buildPayoutSummary();
        OperationsDashboardResponse.ContentSummary content = buildContentSummary();

        List<OperationsDashboardResponse.OperationsAlert> alerts = new ArrayList<>();
        if (availability.errorBudgetRemaining() <= 0) {
            alerts.add(new OperationsDashboardResponse.OperationsAlert(
                    "AVAILABILITY",
                    "CRITICAL",
                    "Availability error budget exhausted. Review closures and staffing outages.",
                    availability.incidents()
            ));
        }
        if (notification.errorBudgetRemaining() <= 0) {
            alerts.add(new OperationsDashboardResponse.OperationsAlert(
                    "NOTIFICATIONS",
                    "MAJOR",
                    "Notification failure budget exceeded. Check provider credentials and retry queues.",
                    notification.incidents()
            ));
        }
        if (content.activeSpasWithoutMedia() > 0) {
            alerts.add(new OperationsDashboardResponse.OperationsAlert(
                    "CONTENT",
                    "MINOR",
                    "Spas are missing hero media. Prompt providers to upload assets.",
                    content.activeSpasWithoutMedia()
            ));
        }

        List<OperationsDashboardResponse.AuditLogView> auditTrail = auditTrailService.recent(20).stream()
                .map(logEntry -> new OperationsDashboardResponse.AuditLogView(
                        logEntry.getId(),
                        logEntry.getActionType(),
                        logEntry.getEntityType(),
                        logEntry.getEntityId(),
                        logEntry.getActor() != null ? logEntry.getActor().getId() : null,
                        logEntry.getCreatedAt(),
                        logEntry.getReason()
                ))
                .toList();

        return new OperationsDashboardResponse(availability, notification, compliance, payouts, content, alerts, auditTrail);
    }

    private OperationsDashboardResponse.SlaReport buildAvailabilitySla(OffsetDateTime now) {
        OffsetDateTime windowStart = now.minusDays(slaProperties.getAvailabilityWindowDays());
        List<Closure> closures = closureRepository.findByEndTsAfter(windowStart);
        long totalMinutes = Duration.between(windowStart, now).toMinutes();
        long downtimeMinutes = closures.stream()
                .mapToLong(c -> computeOverlapMinutes(windowStart, now, c))
                .sum();
        long allowedDowntime = Math.round(totalMinutes * (1 - slaProperties.getAvailabilityTarget()));
        double achieved = totalMinutes == 0
                ? 1.0
                : 1 - (downtimeMinutes / (double) totalMinutes);

        return new OperationsDashboardResponse.SlaReport(
                slaProperties.getAvailabilityTarget(),
                achieved,
                totalMinutes,
                Math.max(0, allowedDowntime - downtimeMinutes),
                closures.size()
        );
    }

    private OperationsDashboardResponse.SlaReport buildNotificationSla(OffsetDateTime now) {
        OffsetDateTime windowStart = now.minusDays(slaProperties.getNotificationWindowDays());
        long total = notificationLogRepository.countByCreatedAtAfter(windowStart);
        long failed = notificationLogRepository.countByStatusInAndCreatedAtAfter(
                List.of(NotificationStatus.FAILED), windowStart);
        long allowedFailures = Math.round(total * (1 - slaProperties.getNotificationTarget()));
        double achieved = total == 0 ? 1.0 : ((double) (total - failed) / total);

        return new OperationsDashboardResponse.SlaReport(
                slaProperties.getNotificationTarget(),
                achieved,
                Duration.between(windowStart, now).toMinutes(),
                Math.max(0, allowedFailures - failed),
                failed
        );
    }

    private OperationsDashboardResponse.ComplianceSummary buildComplianceSummary() {
        long pendingKyc = spaRepository.countByKycStatus(KycStatus.PENDING);
        long unverified = spaRepository.countByIsActiveTrueAndIsVerifiedFalse();
        long erasureQueued = userRepository.countByDataErasureRequestedAtIsNotNull();

        return new OperationsDashboardResponse.ComplianceSummary(pendingKyc, unverified, erasureQueued);
    }

    private OperationsDashboardResponse.PayoutSummary buildPayoutSummary() {
        long pendingPayouts = paymentRepository.countPendingPayouts();
        long settled = paymentRepository.countByPayoutStatusEqualsIgnoreCase("settled");

        return new OperationsDashboardResponse.PayoutSummary(pendingPayouts, settled);
    }

    private OperationsDashboardResponse.ContentSummary buildContentSummary() {
        long spaWithoutMedia = spaRepository.countActiveSpasWithoutMedia();
        long mediaAssets = mediaAssetRepository.count();
        return new OperationsDashboardResponse.ContentSummary(spaWithoutMedia, mediaAssets);
    }

    private long computeOverlapMinutes(OffsetDateTime windowStart, OffsetDateTime windowEnd, Closure closure) {
        OffsetDateTime start = closure.getStartTs().isAfter(windowStart) ? closure.getStartTs() : windowStart;
        OffsetDateTime end = closure.getEndTs().isBefore(windowEnd) ? closure.getEndTs() : windowEnd;
        return Math.max(0, Duration.between(start, end).toMinutes());
    }
}
