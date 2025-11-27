package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.AdminActionType;

import java.time.OffsetDateTime;
import java.util.List;

public record OperationsDashboardResponse(
        SlaReport availabilitySla,
        SlaReport notificationSla,
        ComplianceSummary compliance,
        PayoutSummary payouts,
        ContentSummary content,
        List<OperationsAlert> alerts,
        List<AuditLogView> auditTrail
) {

    public record SlaReport(double target,
                             double achieved,
                             long windowMinutes,
                             long errorBudgetRemaining,
                             long incidents) {
    }

    public record ComplianceSummary(long pendingKyc,
                                     long unverifiedSpas,
                                     long dataErasureQueued) {
    }

    public record PayoutSummary(long pendingPayouts,
                                 long settledPayouts) {
    }

    public record ContentSummary(long activeSpasWithoutMedia,
                                  long totalMediaAssets) {
    }

    public record OperationsAlert(String type,
                                   String severity,
                                   String message,
                                   long count) {
    }

    public record AuditLogView(Long id,
                                AdminActionType actionType,
                                String entityType,
                                Long entityId,
                                Long actorUserId,
                                OffsetDateTime createdAt,
                                String reason) {
    }
}
