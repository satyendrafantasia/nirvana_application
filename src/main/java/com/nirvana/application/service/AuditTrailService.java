package com.nirvana.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.model.AdminActionLog;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.AdminActionType;
import com.nirvana.application.repository.AdminActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditTrailService {

    private final AdminActionLogRepository adminActionLogRepository;
    private final ObjectMapper objectMapper;

    public AdminActionLog record(AdminActionType actionType,
                                 String entityType,
                                 Long entityId,
                                 Long actorUserId,
                                 Object beforeState,
                                 Object afterState,
                                 String reason) {
        AdminActionLog logEntry = new AdminActionLog();
        logEntry.setActionType(actionType);
        logEntry.setEntityType(entityType);
        logEntry.setEntityId(entityId);
        logEntry.setReason(reason);
        if (actorUserId != null) {
            User actor = new User();
            actor.setId(actorUserId);
            logEntry.setActor(actor);
        }

        logEntry.setBeforeStateJson(toJsonSafely(beforeState));
        logEntry.setAfterStateJson(toJsonSafely(afterState));

        AdminActionLog saved = adminActionLogRepository.save(logEntry);
        log.info("Audit trail recorded: {} {} by {}", entityType, actionType, actorUserId);
        return saved;
    }

    public List<AdminActionLog> recent(int limit) {
        return adminActionLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    private String toJsonSafely(Object state) {
        if (state == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit state: {}", e.getMessage());
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
