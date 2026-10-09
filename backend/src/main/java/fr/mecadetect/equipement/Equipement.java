package fr.mecadetect.equipement;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Équipement du parc. Les associations sont en LAZY : la zone et le type ne sont
 * chargés que si on les utilise, ce qui évite des jointures inutiles (voir UC-DB5).
 */
@Entity
@Table(name = "equipement")
public class Equipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_equipement")
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "date_mise_service", nullable = false)
    private LocalDate dateMiseService;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutEquipement statut = StatutEquipement.EN_SERVICE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_type", nullable = false)
    private TypeEquipement type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_zone", nullable = false)
    private Zone zone;

    protected Equipement() {
    }

    public Equipement(String reference, String nom, LocalDate dateMiseService, TypeEquipement type, Zone zone) {
        appliquer(reference, nom, dateMiseService, type, zone);
    }

    /** Modifie les informations descriptives. Interdit sur un équipement archivé. */
    public void modifier(String reference, String nom, LocalDate dateMiseService, TypeEquipement type, Zone zone) {
        verifierNonArchive();
        appliquer(reference, nom, dateMiseService, type, zone);
    }

    /** Suppression logique : l'équipement reste en base pour conserver son historique. */
    public void archiver() {
        if (estArchive()) {
            throw new RegleMetierException("EQUIPEMENT_DEJA_ARCHIVE", "Cet équipement est déjà archivé.");
        }
        this.statut = StatutEquipement.ARCHIVE;
    }

    public boolean estArchive() {
        return statut == StatutEquipement.ARCHIVE;
    }

    private void verifierNonArchive() {
        if (estArchive()) {
            throw new RegleMetierException("EQUIPEMENT_ARCHIVE", "Un équipement archivé ne peut plus être modifié.");
        }
    }

    private void appliquer(String reference, String nom, LocalDate dateMiseService, TypeEquipement type, Zone zone) {
        this.reference = Objects.requireNonNull(reference, "reference").trim();
        this.nom = Objects.requireNonNull(nom, "nom").trim();
        this.dateMiseService = Objects.requireNonNull(dateMiseService, "dateMiseService");
        this.type = Objects.requireNonNull(type, "type");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getNom() {
        return nom;
    }

    public LocalDate getDateMiseService() {
        return dateMiseService;
    }

    public StatutEquipement getStatut() {
        return statut;
    }

    public TypeEquipement getType() {
        return type;
    }

    public Zone getZone() {
        return zone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Equipement autre)) return false;
        return getId() != null && getId().equals(autre.getId());
    }

    @Override
    public int hashCode() {
        return Equipement.class.hashCode();
    }
}