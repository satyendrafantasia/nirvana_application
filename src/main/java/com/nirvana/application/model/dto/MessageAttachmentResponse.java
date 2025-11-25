package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.ModerationStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageAttachmentResponse {
    Long id;
    String fileUrl;
    String fileName;
    String contentType;
    Long sizeBytes;
    ModerationStatus moderationStatus;
    String moderationNote;
}
