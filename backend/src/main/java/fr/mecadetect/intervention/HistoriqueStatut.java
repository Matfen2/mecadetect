package fr.mecadetect.intervention;

import fr.mecadetect.utilisateur.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/// Trace d'un changement de statut (RG-08). Lecture seule côté Java : la table
/// sera alimentée par un trigger Oracle au Sprint 4 (UC-DB1), quel que soit
/// le chemin de modification, y compris une requête SQL directe.
@Entity
@Immutable
@Table(name = "historique_statut")
public class HistoriqueStatut {

    @Id
    @Column(name = "id_historique")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ancien_statut", length = 20)
    private StatutIntervention ancienStatut;

    @Enumerated(EnumType.STRING)
    @Column(name = "nouveau_statut", nullable = false, length = 20)
    private StatutIntervention nouveauStatut;

    @Column(name = "date_changement", nullable = false)
    private LocalDateTime dateChangement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_intervention", nullable = false)
    private Intervention intervention;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_auteur", nullable = false)
    private Utilisateur auteur;

    protected HistoriqueStatut() {
    }

    public Long getId() {
        return id;
    }

    public StatutIntervention getAncienStatut() {
        return ancienStatut;
    }

    public StatutIntervention getNouveauStatut() {
        return nouveauStatut;
    }

    public LocalDateTime getDateChangement() {
        return dateChangement;
    }

    public Intervention getIntervention() {
        return intervention;
    }

    public Utilisateur getAuteur() {
        return auteur;
    }
}