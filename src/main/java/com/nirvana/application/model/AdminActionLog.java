package com.nirvana.application.model;
import com.nirvana.application.model.enums.AdminActionType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admin_action_log", indexes = {
        @Index(name = "idx_admin_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_admin_actor", columnList = "actor_user_id,created_at")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AdminActionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who did it (admin/spa manager)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    @ToString.Exclude
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private AdminActionType actionType;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType; // "SPA", "SERVICE", "BOOKING", etc.

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "before_state", columnDefinition = "json")
    private String beforeStateJson;

    @Column(name = "after_state", columnDefinition = "json")
    private String afterStateJson;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Version
    private Long version;

    // getters/setters
}
