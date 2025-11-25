package com.nirvana.application.model;

import com.nirvana.application.model.enums.ModerationStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "message_attachment", indexes = {
        @Index(name = "idx_message_attachment_message", columnList = "message_id"),
        @Index(name = "idx_message_attachment_status", columnList = "moderation_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 32)
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @Column(name = "moderation_note", length = 500)
    private String moderationNote;
}
