package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.NotificationChannel;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationTemplateResponse {
    Long id;
    String code;
    NotificationChannel channel;
    String name;
    String subject;
    String body;
    String locale;
    String description;
    Boolean enabled;
}
