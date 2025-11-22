package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "provider_user", indexes = {
        @Index(name = "idx_provider_email", columnList = "email"),
        @Index(name = "idx_provider_phone", columnList = "phone")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProviderUser extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String phone;

    @Column(unique = true)
    private String email;

    private String name;
    private String displayName;
    private String title; // e.g., "Senior Therapist"
    @Column(name = "employee_code", unique = true)
    private String employeeCode;

    // employment
    @Column(name = "hire_date")
    private OffsetDateTime hireDate;
    @Column(name = "termination_date")
    private OffsetDateTime terminationDate;
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    @Column(name = "work_hours_json", columnDefinition = "json")
    private String workHoursJson; // detailed schedule

    // credentials & certifications
    @Column(name = "certifications", columnDefinition = "json")
    private String certificationsJson;
    @Column(name = "id_doc_url")
    private String idDocUrl;

    // payout & tax
    @Column(name = "bank_account_masked")
    private String bankAccountMasked;
    @Column(name = "payout_method")
    private String payoutMethod;

    // extra
    @Column(name = "languages", columnDefinition = "json")
    private String[] languages;
    @Column(name = "rating_avg")
    private Float ratingAvg = 0f;
    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;
}
