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

