package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unidirectional relationship due to business logic and performance considerations
    // Logic: Querying Availability based on specific dates, rather than getting all Availability entities for a Spa
    @ManyToOne
    @JoinColumn(nullable = false)
    private Spa Spa;

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Room room;

    @Column(nullable = false)
    private int availableRooms;

    @Override
    public String toString() {
        return "Availability{" +
                "id=" + id +
                ", Spa=" + Spa +
                ", date=" + date +
                ", room=" + room +
                ", availableRooms=" + availableRooms +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Availability that = (Availability) o;
        return Objects.equals(id, that.id) && Objects.equals(Spa, that.Spa) && Objects.equals(room, that.room);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, Spa, room);
    }
}
