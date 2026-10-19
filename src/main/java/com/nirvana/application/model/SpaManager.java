package com.nirvana.application.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaManager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;


    @Column(name = "upi_id", length = 255)
    private String upiId;

    @Column(name = "upi_qr_image_url", length = 1000)
    private String upiQrImageUrl;



    @OneToMany(mappedBy = "spaManager", fetch = FetchType.LAZY)
    private List<Spa> spas = new ArrayList<>();

    @Override
    public String toString() {
        return "SpaManager{" +
                "id=" + id +
                ", user=" + user +
                ", spaManagerId=" + id +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpaManager that = (SpaManager) o;
        return Objects.equals(id, that.id) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, user);
    }
}
