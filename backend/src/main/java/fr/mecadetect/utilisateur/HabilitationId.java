package fr.mecadetect.utilisateur;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Clé composée de la table habilitation : (type d'équipement, technicien). */
@Embeddable
public class HabilitationId implements Serializable {

    @Column(name = "id_type")
    private Long idType;

    @Column(name = "id_technicien")
    private Long idTechnicien;

    protected HabilitationId() {
    }

    public HabilitationId(Long idType, Long idTechnicien) {
        this.idType = Objects.requireNonNull(idType, "idType");
        this.idTechnicien = Objects.requireNonNull(idTechnicien, "idTechnicien");
    }

    public Long getIdType() {
        return idType;
    }

    public Long getIdTechnicien() {
        return idTechnicien;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HabilitationId autre)) return false;
        return Objects.equals(idType, autre.idType) && Objects.equals(idTechnicien, autre.idTechnicien);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idType, idTechnicien);
    }
}