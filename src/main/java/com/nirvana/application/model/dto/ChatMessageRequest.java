package com.nirvana.application.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatMessageRequest {

    @NotBlank
    @jakarta.validation.constraints.Size(max = 4000)
    private String content;

    @Valid
    private List<MessageAttachmentRequest> attachments;
}
