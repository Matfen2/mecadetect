package fr.mecadetect.utilisateur;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.referentiel.TypeEquipement;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Association porteuse ETRE_HABILITE du MCD : un technicien est habilité
 * sur un type d'équipement depuis une date donnée (RG-09).
 */
@Entity
@Table(name = "habilitation")
public class Habilitation {

    @EmbeddedId
    private HabilitationId id;

    @MapsId("idType")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_type")
    private TypeEquipement type;

    @MapsId("idTechnicien")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_technicien")
    private Utilisateur technicien;

    @Column(name = "date_habilitation", nullable = false)
    private LocalDate dateHabilitation;

    protected Habilitation() {
    }

    public Habilitation(TypeEquipement type, Utilisateur technicien, LocalDate dateHabilitation) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(technicien, "technicien");
        if (technicien.getRole() != Role.TECHNICIEN) {
            throw new RegleMetierException("TECHNICIEN_INVALIDE",
                    "Seul un technicien peut recevoir une habilitation.");
        }
        this.id = new HabilitationId(type.getId(), technicien.getId());
        this.type = type;
        this.technicien = technicien;
        this.dateHabilitation = Objects.requireNonNull(dateHabilitation, "dateHabilitation");
    }

    public HabilitationId getId() {
        return id;
    }

    public TypeEquipement getType() {
        return type;
    }

    public Utilisateur getTechnicien() {
        return technicien;
    }

    public LocalDate getDateHabilitation() {
        return dateHabilitation;
    }
}