package com.nirvana.application.model;

import com.nirvana.application.model.enums.ModerationStatus;
import com.nirvana.application.model.enums.SenderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_thread", columnList = "thread_id,created_at"),
        @Index(name = "idx_chat_message_moderation", columnList = "moderation_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private MessageThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 32)
    private SenderType senderType;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 32)
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @Column(name = "moderation_note", length = 500)
    private String moderationNote;

    @Column(name = "moderated_at")
    private OffsetDateTime moderatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessageAttachment> attachments = new ArrayList<>();
}
