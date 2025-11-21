package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String addressLine2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @Override
    public String toString() {
        return "Address{" +
                "id=" + id +
                ", addressLine1='" + addressLine + '\'' +
                ", addressLine2='" + addressLine2 + '\'' +
                ", city='" + city + '\'' +
                ", country='" + country + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address)) return false;
        Address other = (Address) o;
        // If both entities have an id, use it for equality (database identity)
        if (this.id != null && other.id != null) {
            return this.id.equals(other.id);
        }
        // Otherwise compare on natural fields
        return Objects.equals(addressLine, other.addressLine) &&
                Objects.equals(addressLine2, other.addressLine2) &&
                Objects.equals(city, other.city) &&
                Objects.equals(country, other.country);
    }

    @Override
    public int hashCode() {
        // If id is available use it (stable after persist), otherwise use natural fields
        return (id != null) ? id.hashCode() : Objects.hash(addressLine, addressLine2, city, country);
    }

}
