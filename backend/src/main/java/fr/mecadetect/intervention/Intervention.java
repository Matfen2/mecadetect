package fr.mecadetect.intervention;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.intervention.Transition.Annulation;
import fr.mecadetect.intervention.Transition.Cloture;
import fr.mecadetect.intervention.Transition.Demarrage;
import fr.mecadetect.intervention.Transition.Planification;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
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
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

/// Intervention de maintenance sur un équipement.
/// Les changements de statut passent tous par [CycleDeVie] : aucun setter public
/// ne permet de contourner le cycle de vie. Les dates sont en UTC.
@Entity
@Table(name = "intervention")
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_intervention")
    private Long id;

    @Column(name = "numero", nullable = false, unique = true, length = 20)
    private String numero;

    @Column(name = "titre", nullable = false, length = 100)
    private String titre;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priorite", nullable = false, length = 10)
    private Priorite priorite;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutIntervention statut;

    @Column(name = "date_demande", nullable = false)
    private LocalDateTime dateDemande;

    @Column(name = "date_planifiee")
    private LocalDateTime datePlanifiee;

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    @Column(name = "temps_passe_min")
    private Integer tempsPasseMin;

    @Column(name = "compte_rendu", length = 2000)
    private String compteRendu;

    @Column(name = "motif_annulation", length = 500)
    private String motifAnnulation;

    /// Verrouillage optimiste (UC-DB6) : deux modifications simultanées, la seconde échoue.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_equipement", nullable = false)
    private Equipement equipement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_demandeur", nullable = false)
    private Utilisateur demandeur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_technicien")
    private Utilisateur technicien;

    /// Auteur du dernier changement : lu par le trigger d'historisation (Sprint 4).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_modifie_par", nullable = false)
    private Utilisateur modifiePar;

    protected Intervention() {
    }

    /// Déclaration d'une panne : l'intervention naît au statut DEMANDEE.
    ///
    /// @param numero      numéro métier, fourni par la séquence Oracle
    /// @param titre       titre court de la panne
    /// @param description description détaillée
    /// @param priorite    priorité demandée
    /// @param equipement  équipement en panne, non archivé
    /// @param demandeur   utilisateur qui déclare la panne
    /// @param dateDemande date de la déclaration, en UTC
    /// @throws RegleMetierException si l'équipement est archivé
    public Intervention(String numero, String titre, String description, Priorite priorite,
                        Equipement equipement, Utilisateur demandeur, LocalDateTime dateDemande) {
        Objects.requireNonNull(equipement, "equipement");
        if (equipement.estArchive()) {
            throw new RegleMetierException("EQUIPEMENT_ARCHIVE",
                    "Un équipement archivé ne peut plus recevoir de nouvelle intervention.");
        }
        this.numero = Objects.requireNonNull(numero, "numero");
        this.titre = Objects.requireNonNull(titre, "titre").trim();
        this.description = Objects.requireNonNull(description, "description").trim();
        this.priorite = Objects.requireNonNull(priorite, "priorite");
        this.equipement = equipement;
        this.demandeur = Objects.requireNonNull(demandeur, "demandeur");
        this.dateDemande = Objects.requireNonNull(dateDemande, "dateDemande");
        this.statut = StatutIntervention.DEMANDEE;
        this.modifiePar = demandeur;
    }

    /// DEMANDEE → PLANIFIEE, avec assignation d'un technicien.
    ///
    /// @param technicien    technicien assigné, actif
    /// @param datePlanifiee date et heure prévues
    /// @param auteur        admin qui planifie
    /// @throws RegleMetierException si la transition est interdite ou si l'utilisateur n'est pas un technicien actif
    public void planifier(Utilisateur technicien, LocalDateTime datePlanifiee, Utilisateur auteur) {
        var transition = new Planification(technicien, datePlanifiee);
        var nouveauStatut = CycleDeVie.statutApres(statut, transition);
        if (technicien.getRole() != Role.TECHNICIEN || !technicien.isActif()) {
            throw new RegleMetierException("TECHNICIEN_INVALIDE",
                    "Une intervention ne peut être assignée qu'à un technicien actif.");
        }
        this.technicien = technicien;
        this.datePlanifiee = transition.datePlanifiee();
        changerStatut(nouveauStatut, auteur);
    }

    /// PLANIFIEE → EN_COURS, par le technicien assigné uniquement (RG-02).
    ///
    /// @param auteur     utilisateur qui démarre l'intervention
    /// @param maintenant date de début, en UTC
    /// @throws RegleMetierException         si la transition est interdite
    /// @throws ActionNonAutoriseeException si l'auteur n'est pas le technicien assigné
    public void demarrer(Utilisateur auteur, LocalDateTime maintenant) {
        var transition = new Demarrage(maintenant);
        var nouveauStatut = CycleDeVie.statutApres(statut, transition);
        verifierTechnicienAssigne(auteur);
        this.dateDebut = transition.dateDebut();
        changerStatut(nouveauStatut, auteur);
    }

    /// EN_COURS → TERMINEE, avec compte rendu et temps passé (RG-02, RG-03).
    ///
    /// @param compteRendu   compte rendu de l'intervention
    /// @param tempsPasseMin temps passé, en minutes
    /// @param auteur        utilisateur qui clôture l'intervention
    /// @param maintenant    date de fin, en UTC
    /// @throws RegleMetierException         si la transition est interdite
    /// @throws ActionNonAutoriseeException si l'auteur n'est pas le technicien assigné
    public void cloturer(String compteRendu, int tempsPasseMin, Utilisateur auteur, LocalDateTime maintenant) {
        var transition = new Cloture(compteRendu, tempsPasseMin, maintenant);
        var nouveauStatut = CycleDeVie.statutApres(statut, transition);
        verifierTechnicienAssigne(auteur);
        this.compteRendu = transition.compteRendu();
        this.tempsPasseMin = transition.tempsPasseMin();
        this.dateFin = transition.dateFin();
        changerStatut(nouveauStatut, auteur);
    }

    /// DEMANDEE ou PLANIFIEE → ANNULEE, avec un motif (RG-03).
    ///
    /// @param motif  motif de l'annulation
    /// @param auteur admin qui annule
    /// @throws RegleMetierException si la transition est interdite
    public void annuler(String motif, Utilisateur auteur) {
        var transition = new Annulation(motif);
        var nouveauStatut = CycleDeVie.statutApres(statut, transition);
        this.motifAnnulation = transition.motif();
        changerStatut(nouveauStatut, auteur);
    }

    /// Indique si l'intervention est encore ouverte : demandée, planifiée ou en cours.
    ///
    /// @return vrai si l'intervention n'est ni terminée ni annulée
    public boolean estOuverte() {
        return StatutIntervention.OUVERTS.contains(statut);
    }

    private void verifierTechnicienAssigne(Utilisateur auteur) {
        if (technicien == null || !technicien.equals(auteur)) {
            throw new ActionNonAutoriseeException("TECHNICIEN_NON_ASSIGNE",
                    "Seul le technicien assigné peut faire avancer cette intervention.");
        }
    }

    private void changerStatut(StatutIntervention nouveauStatut, Utilisateur auteur) {
        this.statut = nouveauStatut;
        this.modifiePar = Objects.requireNonNull(auteur, "auteur");
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public String getTitre() {
        return titre;
    }

    public String getDescription() {
        return description;
    }

    public Priorite getPriorite() {
        return priorite;
    }

    public StatutIntervention getStatut() {
        return statut;
    }

    public LocalDateTime getDateDemande() {
        return dateDemande;
    }

    public LocalDateTime getDatePlanifiee() {
        return datePlanifiee;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public Integer getTempsPasseMin() {
        return tempsPasseMin;
    }

    public String getCompteRendu() {
        return compteRendu;
    }

    public String getMotifAnnulation() {
        return motifAnnulation;
    }

    public Long getVersion() {
        return version;
    }

    public Equipement getEquipement() {
        return equipement;
    }

    public Utilisateur getDemandeur() {
        return demandeur;
    }

    public Utilisateur getTechnicien() {
        return technicien;
    }

    public Utilisateur getModifiePar() {
        return modifiePar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Intervention autre)) return false;
        return getId() != null && getId().equals(autre.getId());
    }

    @Override
    public int hashCode() {
        return Intervention.class.hashCode();
    }
}