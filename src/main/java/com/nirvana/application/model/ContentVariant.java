package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;

@Entity
@Table(name = "content_variant", indexes = {
        @Index(name = "idx_content_experiment", columnList = "experiment_key,variant_key"),
        @Index(name = "idx_content_active", columnList = "experiment_key,is_active")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ContentVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experiment_key", nullable = false, length = 128)
    private String experimentKey;

    @Column(name = "variant_key", nullable = false, length = 128)
    private String variantKey;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale = "en";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
