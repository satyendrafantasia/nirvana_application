package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "idx_appuser_email", columnList = "email"),
        @Index(name = "idx_appuser_phone", columnList = "phone"),
        @Index(name = "idx_appuser_referral", columnList = "referral_code")
})
public class User extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String phone;

    @Column(unique = true)
    private String email;

    private String name;
    private String lastName;
    private String displayName;
    private String username;
    @Column(nullable = false)
    private String password; // encoded

    @Column(nullable = false)
    private boolean active = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false)
    private Role role;

    // store simple role names like ROLE_USER, ROLE_ADMIN
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles;

    // security / auth
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "password_salt")
    private String passwordSalt;
    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = false;
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    // verification & identity
    @Column(name = "phone_verified", nullable = false)
    private Boolean phoneVerified = false;
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;
    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    // locale & preferences
    @Column(name = "timezone", nullable = false)
    private String timezone = "UTC";
    private String locale; // en_IN, en_US
    @Column(name = "preferred_contact_method")
    private String preferredContactMethod; // "phone","email","whatsapp"
    @Column(name = "marketing_opt_in", nullable = false)
    private Boolean marketingOptIn = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private SpaManager spaManager;

    // business / loyalty
    @Column(name = "loyalty_points", nullable = false)
    private Integer loyaltyPoints = 0;
    @Column(name = "referral_code", unique = true)
    private String referralCode;
    @Column(name = "referred_by")
    private String referredByCode;

    // soft delete / status
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // metadata
    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson; // e.g. preferences, third-party ids

    @Version
    private Long version;
}
