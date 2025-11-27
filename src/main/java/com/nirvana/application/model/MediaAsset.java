package com.nirvana.application.model;
import com.nirvana.application.model.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "media_asset", indexes = {
        @Index(name = "idx_media_asset_spa", columnList = "spa_id,media_type,position")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_id", nullable = false)
    private Spa spa;

    /**
     * Backward-compatible column retained for older datasets and schema validation paths.
     * Mapped as read-only to mirror {@code spa_id} without driving writes.
     */
    @Column(name = "entity_id", insertable = false, updatable = false)
    private Long legacyEntityId;

    @Column(name = "object_key", nullable = false, length = 1000)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    private MediaType mediaType = MediaType.IMAGE;

    @Column(name = "title")
    private String title;

    @Column(name = "position")
    private Integer position; // for ordering
}

