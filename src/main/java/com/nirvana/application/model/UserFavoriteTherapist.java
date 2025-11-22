package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_favorite_therapist", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_therapist_fav", columnNames = {"user_id", "therapist_id"})
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserFavoriteTherapist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "therapist_id", nullable = false)
    @ToString.Exclude
    private Therapist therapist;

    @Version
    private Long version;

    // getters/setters
}

