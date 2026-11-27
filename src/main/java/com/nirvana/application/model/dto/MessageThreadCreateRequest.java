package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class MessageThreadCreateRequest {

    private Long bookingId;

    @NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String subject;

    @jakarta.validation.constraints.Size(max = 4000)
    private String initialMessage;

    private List<MessageAttachmentRequest> attachments;
}
