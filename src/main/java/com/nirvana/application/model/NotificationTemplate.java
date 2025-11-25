package com.nirvana.application.model;

import com.nirvana.application.model.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_template", indexes = {
        @Index(name = "uk_notification_template_code_channel", columnList = "code,channel", unique = true),
        @Index(name = "idx_notification_template_active", columnList = "enabled")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 128)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "locale", length = 16)
    private String locale;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
}
