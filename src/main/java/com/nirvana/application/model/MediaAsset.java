package com.nirvana.application.model;
import com.nirvana.application.model.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "media_asset", indexes = {
        @Index(name = "idx_media_entity", columnList = "entity_type,entity_id,position")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType; // SPA, SERVICE, THERAPIST, REVIEW

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    private MediaType mediaType = MediaType.IMAGE;

    @Column(name = "position")
    private Integer position; // for ordering

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;

    // getters/setters
}

