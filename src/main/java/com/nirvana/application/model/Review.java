package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "review", indexes = {
        @Index(name = "idx_review_spa", columnList = "spa_id,created_at DESC"),
        @Index(name = "idx_review_user", columnList = "user_id,created_at DESC")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    @Column(nullable = false)
    private Short rating; // 1..5

    private String title;

    @Column(length = 4000)
    private String text;


    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // expanded
    @Column(name = "reply_text", length = 2000)
    private String replyText;
    @Column(name = "reply_by_provider_id")
    private Long replyByProviderId;
    @Column(name = "reply_at")
    private OffsetDateTime replyAt;

    // link to media assets, filtered by entity_type='REVIEW'
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", referencedColumnName = "id")
    @org.hibernate.annotations.Where(clause = "entity_type = 'REVIEW'")
    private java.util.List<MediaAsset> mediaAssets;

    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount = 0;
    @Column(name = "reported_count", nullable = false)
    private Integer reportedCount = 0;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;
}

