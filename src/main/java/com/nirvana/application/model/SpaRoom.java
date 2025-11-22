package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spa_room", indexes = {
        @Index(name = "idx_room_spa", columnList = "spa_id,name")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SpaRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spa_id", nullable = false)
    @ToString.Exclude
    private Spa spa;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "meta", columnDefinition = "json")
    private String metaJson;

    @Version
    private Long version;

    // getters/setters
}

