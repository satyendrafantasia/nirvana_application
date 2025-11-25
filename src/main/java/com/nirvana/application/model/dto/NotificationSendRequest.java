package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class NotificationSendRequest {

    @NotNull
    private NotificationChannel channel;

    @NotBlank
    private String templateCode;

    private String destination;

    private Map<String, String> variables;
}
