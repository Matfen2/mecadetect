package fr.mecadetect.referentiel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/** Type d'équipement (presse, tour CN, compresseur...). Sert aussi aux habilitations. */
@Entity
@Table(name = "type_equipement")
public class TypeEquipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_type")
    private Long id;

    @Column(name = "libelle", nullable = false, unique = true, length = 100)
    private String libelle;

    protected TypeEquipement() {
    }

    public TypeEquipement(String libelle) {
        this.libelle = Objects.requireNonNull(libelle, "libelle");
    }

    public Long getId() {
        return id;
    }

    public String getLibelle() {
        return libelle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeEquipement autre)) return false;
        return getId() != null && getId().equals(autre.getId());
    }

    @Override
    public int hashCode() {
        return TypeEquipement.class.hashCode();
    }
}