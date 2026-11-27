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
    private final jakarta.persistence.EntityManager entityManager;

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
        logEntry.setReason(com.nirvana.application.config.SentryScrubbing.scrub(reason));
        if (actorUserId != null) {
            logEntry.setActor(entityManager.getReference(User.class, actorUserId));
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
            var tree = objectMapper.valueToTree(state);
            redact(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Failed to serialize audit state: {}", e.getClass().getSimpleName());
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    // Stored audit history has a different lifetime than the request that produced it.
    static void redact(com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
            var fields = obj.properties().iterator();
            while (fields.hasNext()) {
                var field = fields.next();
                String key = field.getKey().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (key.matches(".*(password|secret|credential|token|signature|authorization|cookie|otp|email|phone|address|bankaccount|accountnumber|privatekey|apikey|taxid|gstin|specialrequests|medical|diagnosis).*")) {
                    fields.remove();
                } else if (field.getValue().isTextual()) {
                    field.setValue(com.fasterxml.jackson.databind.node.TextNode.valueOf(
                            com.nirvana.application.config.SentryScrubbing.scrub(field.getValue().textValue())));
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                var value = array.get(i);
                if (value.isTextual()) array.set(i, com.nirvana.application.config.SentryScrubbing.scrub(value.textValue()));
                else redact(value);
            }
        }
    }
}
