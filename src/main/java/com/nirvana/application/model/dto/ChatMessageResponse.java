package com.nirvana.application.model.dto;

import com.nirvana.application.model.enums.ModerationStatus;
import com.nirvana.application.model.enums.SenderType;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class ChatMessageResponse {
    Long id;
    Long threadId;
    SenderType senderType;
    String content;
    ModerationStatus moderationStatus;
    String moderationNote;
    OffsetDateTime createdAt;
    OffsetDateTime moderatedAt;
    List<MessageAttachmentResponse> attachments;
}
