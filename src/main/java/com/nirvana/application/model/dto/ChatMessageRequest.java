package com.nirvana.application.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatMessageRequest {

    @NotBlank
    private String content;

    @Valid
    private List<MessageAttachmentRequest> attachments;
}
