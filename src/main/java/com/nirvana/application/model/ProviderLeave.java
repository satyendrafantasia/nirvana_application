package com.nirvana.application.model;
import com.nirvana.application.model.enums.LeaveStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "provider_leave", indexes = {
        @Index(name = "idx_leave_provider", columnList = "provider_user_id,start_ts,end_ts")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProviderLeave extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_user_id", nullable = false)
    @ToString.Exclude
    private ProviderUser providerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spa_id", nullable = false)
    @ToString.Exclude
    private Spa spa;

    @Column(name = "start_ts", nullable = false)
    private OffsetDateTime startTs;

    @Column(name = "end_ts", nullable = false)
    private OffsetDateTime endTs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LeaveStatus status = LeaveStatus.REQUESTED;

    @Column(name = "reason", length = 1000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    @ToString.Exclude
    private User approvedBy;

    @Version
    private Long version;


}
