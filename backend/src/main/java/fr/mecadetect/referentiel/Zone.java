package fr.mecadetect.referentiel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/** Zone géographique du site (atelier, bâtiment...). */
@Entity
@Table(name = "zone")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_zone")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    protected Zone() {
    }

    public Zone(String code, String libelle) {
        this.code = Objects.requireNonNull(code, "code");
        this.libelle = Objects.requireNonNull(libelle, "libelle");
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    // Compatible avec les proxies Hibernate (chargement LAZY) :
    // on passe par getId() et on n'utilise pas getClass(), qui diffère pour un proxy
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Zone autre)) return false;
        return getId() != null && getId().equals(autre.getId());
    }

    @Override
    public int hashCode() {
        return Zone.class.hashCode();
    }
}