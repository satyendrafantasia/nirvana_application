package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationTemplateRequest {

    @NotBlank
    private String code;

    @NotNull
    private NotificationChannel channel;

    @NotBlank
    private String name;

    private String subject;

    private String body;

    private String locale;

    private String description;

    private Boolean enabled;
}
