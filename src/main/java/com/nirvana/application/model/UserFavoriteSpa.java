package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_favorite_spa", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_spa_fav", columnNames = {"user_id", "spa_id"})
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserFavoriteSpa extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spa_id", nullable = false)
    @ToString.Exclude
    private Spa spa;

    @Version
    private Long version;

    // getters/setters
}
