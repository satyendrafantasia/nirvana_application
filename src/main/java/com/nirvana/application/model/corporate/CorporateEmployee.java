package com.nirvana.application.model.corporate;

import com.nirvana.application.model.BaseEntity;
import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.CorporateEmployeeStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "corporate_employee", indexes = {
        @Index(name = "idx_corporate_employee_email", columnList = "employee_email, corporate_id", unique = true)
})
public class CorporateEmployee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_id", nullable = false)
    private Corporate corporate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "employee_email", nullable = false, length = 255)
    private String employeeEmail;

    @Column(name = "employee_identifier", length = 128)
    private String employeeIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CorporateEmployeeStatus status = CorporateEmployeeStatus.INVITED;
}
