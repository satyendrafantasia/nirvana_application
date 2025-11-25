package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.MessageThreadStatus;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class MessageThreadResponse {
    Long threadId;
    Long bookingId;
    String subject;
    MessageThreadStatus status;
    OffsetDateTime lastMessageAt;
    Integer unreadCount;
}
