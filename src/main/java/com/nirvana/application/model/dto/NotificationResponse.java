package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class NotificationResponse {
    Long id;
    NotificationChannel channel;
    NotificationStatus status;
    String templateCode;
    String destination;
    String title;
    String message;
    OffsetDateTime sentAt;
    OffsetDateTime readAt;
    OffsetDateTime createdAt;
}
