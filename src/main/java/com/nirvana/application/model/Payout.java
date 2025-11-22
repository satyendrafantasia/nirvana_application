package com.nirvana.application.model;

import com.nirvana.application.model.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payout", indexes = {
        @Index(name = "idx_payout_spa", columnList = "spa_id,payout_date"),
        @Index(name = "idx_payout_status", columnList = "status")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Payout extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spa_id", nullable = false)
    @ToString.Exclude
    private Spa spa;

    @Column(name = "payout_reference", length = 128, unique = true)
    private String payoutReference;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "payout_date")
    private OffsetDateTime payoutDate;

    @Column(name = "bank_account_masked", length = 255)
    private String bankAccountMasked;

    // list of payment IDs included in this payout (or use join table later)
    @Column(name = "payment_ids", columnDefinition = "json")
    private String paymentIdsJson;

    @Column(name = "fees_cents")
    private Integer feesCents;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;

    // getters/setters
}

