package com.nirvana.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class MessageThreadCreateRequest {

    private Long bookingId;

    @NotBlank
    private String subject;

    private String initialMessage;

    private List<MessageAttachmentRequest> attachments;
}
