package com.nirvana.application.model;
import com.nirvana.application.model.enums.NotificationChannel;
import com.nirvana.application.model.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_log", indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id,created_at"),
        @Index(name = "idx_notification_channel", columnList = "channel,status")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NULLABLE: sometimes you send to spa/phone without user entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationStatus status = NotificationStatus.QUEUED;

    @Column(name = "template_code", length = 128)
    private String templateCode;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination; // email or phone

    @Column(name = "payload", columnDefinition = "json")
    private String payloadJson;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Version
    private Long version;

    // getters/setters
}
