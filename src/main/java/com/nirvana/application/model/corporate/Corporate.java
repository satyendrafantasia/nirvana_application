package com.nirvana.application.model.corporate;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.enums.CorporateDealStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "corporate", indexes = {
        @Index(name = "idx_corporate_domain", columnList = "domain", unique = true)
})
public class Corporate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String domain;

    @Column(name = "contact_person", length = 255)
    private String contactPerson;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CorporateDealStatus status = CorporateDealStatus.ACTIVE;
}
