package fr.mecadetect.intervention.application;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.erreur.ConflitVersionException;
import fr.mecadetect.erreur.DonneesInvalidesException;
import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.erreur.RessourceIntrouvableException;
import fr.mecadetect.intervention.domain.DemandePage;
import fr.mecadetect.intervention.domain.Habilitations;
import fr.mecadetect.intervention.domain.HistoriqueStatutRepository;
import fr.mecadetect.intervention.domain.Intervention;
import fr.mecadetect.intervention.domain.InterventionRepository;
import fr.mecadetect.intervention.domain.InterventionResume;
import fr.mecadetect.intervention.domain.PageResultat;
import fr.mecadetect.intervention.domain.ParcEquipements;
import fr.mecadetect.intervention.domain.StatutIntervention;
import fr.mecadetect.intervention.domain.Utilisateurs;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/// Cas d'usage des interventions. Les règles propres à une intervention sont dans l'entité ;
/// le service porte celles qui ont besoin des autres données (équipement, utilisateurs, habilitations).
///
/// Classe Java sans framework : elle ne dépend que des ports du domaine. Elle devient un bean
/// Spring, avec ses transactions, dans la configuration de l'infrastructure
/// (`ConfigurationInterventions`), et se teste sans Spring.
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final ParcEquipements parcEquipements;
    private final Utilisateurs utilisateurs;
    private final Habilitations habilitations;
    private final Clock clock;

    /// @param interventionRepository persistance des interventions
    /// @param historiqueRepository   lecture de l'historique des statuts
    /// @param parcEquipements        accès aux équipements
    /// @param utilisateurs           accès aux comptes utilisateurs
    /// @param habilitations          habilitations des techniciens
    /// @param clock                  horloge de l'application, en UTC
    public InterventionService(InterventionRepository interventionRepository,
                               HistoriqueStatutRepository historiqueRepository,
                               ParcEquipements parcEquipements,
                               Utilisateurs utilisateurs,
                               Habilitations habilitations,
                               Clock clock) {
        this.interventionRepository = interventionRepository;
        this.historiqueRepository = historiqueRepository;
        this.parcEquipements = parcEquipements;
        this.utilisateurs = utilisateurs;
        this.habilitations = habilitations;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ lecture

    /// Détail d'une intervention et son historique.
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @return l'intervention et son historique
    /// @throws RessourceIntrouvableException si l'intervention n'existe pas
    public DetailIntervention obtenir(Long idIntervention) {
        return detail(chargerIntervention(idIntervention));
    }

    /// Recherche paginée des interventions.
    ///
    /// @param criteres filtres, tous facultatifs ; la période porte sur la date de demande
    /// @param page     page, taille et tri demandés
    /// @return la page de résumés d'interventions
    public PageResultat<InterventionResume> rechercher(CriteresRechercheIntervention criteres, DemandePage page) {
        return interventionRepository.rechercher(
                criteres.statut(), criteres.priorite(), criteres.idTechnicien(), null,
                criteres.depuis() == null ? null : criteres.depuis().atStartOfDay(),
                // jusqua est inclus : on prend tout ce qui précède le lendemain à minuit
                criteres.jusqua() == null ? null : criteres.jusqua().plusDays(1).atStartOfDay(),
                page);
    }

    /// Les interventions planifiées ou en cours d'un technicien, celle en cours d'abord.
    ///
    /// @param idTechnicien identifiant du technicien
    /// @param page         page et taille demandées
    /// @return la page de résumés d'interventions
    public PageResultat<InterventionResume> aTraiterPar(Long idTechnicien, DemandePage page) {
        return interventionRepository.aTraiterPar(idTechnicien,
                Set.of(StatutIntervention.PLANIFIEE, StatutIntervention.EN_COURS), page);
    }

    /// Historique des interventions d'un équipement, les plus récentes d'abord.
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @param page         page, taille et tri demandés
    /// @return la page de résumés d'interventions
    /// @throws RessourceIntrouvableException si l'équipement n'existe pas
    public PageResultat<InterventionResume> historiqueEquipement(Long idEquipement, DemandePage page) {
        if (!parcEquipements.existe(idEquipement)) {
            throw equipementIntrouvable(idEquipement);
        }
        return interventionRepository.rechercher(null, null, null, idEquipement, null, null, page);
    }

    // ------------------------------------------------------------------ écriture

    /// Déclaration d'une panne : intervention DEMANDEE et équipement EN_PANNE (RG-06, RG-07).
    ///
    /// @param commande données de la panne
    /// @param idAuteur identifiant de l'utilisateur connecté
    /// @return l'intervention créée et son historique
    /// @throws RessourceIntrouvableException si l'équipement n'existe pas
    /// @throws RegleMetierException          si l'équipement est archivé
    /// @throws ActionNonAutoriseeException   si l'utilisateur est inconnu ou désactivé
    public DetailIntervention declarer(DeclarationCommande commande, Long idAuteur) {
        var auteur = chargerUtilisateur(idAuteur);
        var equipement = chargerEquipement(commande.idEquipement());
        var maintenant = maintenant();

        var intervention = new Intervention(
                interventionRepository.prochainNumero(maintenant.getYear()),
                commande.titre(), commande.description(), commande.priorite(),
                equipement, auteur, maintenant);
        equipement.signalerPanne();

        return detail(interventionRepository.enregistrer(intervention));
    }

    /// DEMANDEE → PLANIFIEE : l'admin assigne un technicien habilité (RG-01, RG-09).
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @param commande       technicien, date prévue et version affichée
    /// @param idAuteur       identifiant de l'admin connecté
    /// @return l'intervention planifiée et son historique
    /// @throws RessourceIntrouvableException     si l'intervention n'existe pas
    /// @throws DonneesInvalidesException         si le technicien n'existe pas
    /// @throws RegleMetierException              si le technicien n'est pas habilité ou si la transition est interdite
    /// @throws ConflitVersionException           si l'intervention a été modifiée entre-temps (UC-DB6)
    public DetailIntervention planifier(Long idIntervention, PlanificationCommande commande, Long idAuteur) {
        var auteur = chargerUtilisateur(idAuteur);
        var intervention = chargerIntervention(idIntervention);
        verifierVersion(intervention, commande.version());

        var technicien = utilisateurs.trouver(commande.idTechnicien())
                .orElseThrow(() -> new DonneesInvalidesException("TECHNICIEN_INCONNU",
                        inexistant("Le technicien", commande.idTechnicien())));
        var idType = intervention.getEquipement().getType().getId();
        if (technicien.getRole() == Role.TECHNICIEN
                && !habilitations.estHabilite(idType, technicien.getId())) {
            throw new RegleMetierException("TECHNICIEN_NON_HABILITE",
                    "%s %s n'est pas habilité sur les équipements de type « %s »."
                            .formatted(technicien.getPrenom(), technicien.getNom(),
                                    intervention.getEquipement().getType().getLibelle()));
        }

        intervention.planifier(technicien, commande.datePlanifiee(), auteur);
        return detail(interventionRepository.enregistrerEtSynchroniser(intervention));
    }

    /// PLANIFIEE → EN_COURS par le technicien assigné (RG-02), une seule à la fois (RG-04).
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @param version        version de l'intervention affichée au technicien
    /// @param idAuteur       identifiant du technicien connecté
    /// @return l'intervention démarrée et son historique
    /// @throws RessourceIntrouvableException     si l'intervention n'existe pas
    /// @throws RegleMetierException              si le technicien a déjà une intervention en cours
    /// @throws ActionNonAutoriseeException       si l'auteur n'est pas le technicien assigné
    /// @throws ConflitVersionException           si l'intervention a été modifiée entre-temps (UC-DB6)
    public DetailIntervention demarrer(Long idIntervention, long version, Long idAuteur) {
        var auteur = chargerUtilisateur(idAuteur);
        var intervention = chargerIntervention(idIntervention);
        verifierVersion(intervention, version);

        if (auteur.equals(intervention.getTechnicien())
                && interventionRepository.existePourTechnicien(auteur.getId(), StatutIntervention.EN_COURS)) {
            throw new RegleMetierException("INTERVENTION_DEJA_EN_COURS",
                    "Vous avez déjà une intervention en cours : clôturez-la avant d'en démarrer une autre.");
        }

        intervention.demarrer(auteur, maintenant());
        interventionRepository.enregistrerEtSynchroniser(intervention);
        mettreAJourStatutEquipement(intervention.getEquipement());
        return detail(intervention);
    }

    /// EN_COURS → TERMINEE par le technicien assigné, avec compte rendu et temps passé (RG-02, RG-03).
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @param commande       compte rendu, temps passé et version affichée
    /// @param idAuteur       identifiant du technicien connecté
    /// @return l'intervention clôturée et son historique
    /// @throws RessourceIntrouvableException     si l'intervention n'existe pas
    /// @throws ActionNonAutoriseeException       si l'auteur n'est pas le technicien assigné
    /// @throws ConflitVersionException           si l'intervention a été modifiée entre-temps (UC-DB6)
    public DetailIntervention cloturer(Long idIntervention, ClotureCommande commande, Long idAuteur) {
        var auteur = chargerUtilisateur(idAuteur);
        var intervention = chargerIntervention(idIntervention);
        verifierVersion(intervention, commande.version());

        intervention.cloturer(commande.compteRendu(), commande.tempsPasseMin(), auteur, maintenant());
        interventionRepository.enregistrerEtSynchroniser(intervention);
        mettreAJourStatutEquipement(intervention.getEquipement());
        return detail(intervention);
    }

    /// DEMANDEE ou PLANIFIEE → ANNULEE par l'admin, avec un motif (RG-01, RG-03).
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @param commande       motif et version affichée
    /// @param idAuteur       identifiant de l'admin connecté
    /// @return l'intervention annulée et son historique
    /// @throws RessourceIntrouvableException     si l'intervention n'existe pas
    /// @throws RegleMetierException              si l'intervention est déjà en cours ou close
    /// @throws ConflitVersionException           si l'intervention a été modifiée entre-temps (UC-DB6)
    public DetailIntervention annuler(Long idIntervention, AnnulationCommande commande, Long idAuteur) {
        var auteur = chargerUtilisateur(idAuteur);
        var intervention = chargerIntervention(idIntervention);
        verifierVersion(intervention, commande.version());

        intervention.annuler(commande.motif(), auteur);
        interventionRepository.enregistrerEtSynchroniser(intervention);
        mettreAJourStatutEquipement(intervention.getEquipement());
        return detail(intervention);
    }

    /// RG-05 : recalcule le statut de l'équipement d'après ses interventions en base.
    /// Appelé après le flush de l'intervention, pour que les requêtes voient son nouveau statut.
    private void mettreAJourStatutEquipement(Equipement equipement) {
        equipement.recalculerStatut(
                interventionRepository.existePourEquipement(equipement.getId(), StatutIntervention.EN_COURS),
                interventionRepository.existePourEquipement(equipement.getId(), StatutIntervention.OUVERTS));
        parcEquipements.synchroniser();
    }

    /// UC-DB6 : le client renvoie la version qu'il a affichée. Si elle a changé entre-temps,
    /// quelqu'un d'autre a modifié l'intervention : on refuse plutôt que d'écraser son travail.
    /// La colonne @Version protège en plus le cas de deux requêtes strictement simultanées.
    private static void verifierVersion(Intervention intervention, long versionAttendue) {
        if (intervention.getVersion() != versionAttendue) {
            throw new ConflitVersionException(
                    "Version " + versionAttendue + " attendue, version " + intervention.getVersion() + " en base.");
        }
    }

    private Intervention chargerIntervention(Long idIntervention) {
        return interventionRepository.trouverDetail(idIntervention)
                .orElseThrow(() -> new RessourceIntrouvableException("INTERVENTION_INTROUVABLE",
                        inexistant("L'intervention", idIntervention)));
    }

    private DetailIntervention detail(Intervention intervention) {
        var historique = historiqueRepository.historiqueDe(intervention.getId());
        return new DetailIntervention(intervention, historique);
    }

    private Equipement chargerEquipement(Long idEquipement) {
        return parcEquipements.trouverAvecTypeEtZone(idEquipement)
                .orElseThrow(() -> equipementIntrouvable(idEquipement));
    }

    private static RessourceIntrouvableException equipementIntrouvable(Long idEquipement) {
        return new RessourceIntrouvableException("EQUIPEMENT_INTROUVABLE", inexistant("L'équipement", idEquipement));
    }

    /// Message commun aux éléments introuvables : « L'intervention 42 n'existe pas. »
    private static String inexistant(String element, Long id) {
        return element + " " + id + " n'existe pas.";
    }

    private Utilisateur chargerUtilisateur(Long idUtilisateur) {
        return utilisateurs.trouver(idUtilisateur)
                .filter(Utilisateur::isActif)
                .orElseThrow(() -> new ActionNonAutoriseeException("UTILISATEUR_INCONNU",
                        "Utilisateur connecté non reconnu ou désactivé."));
    }

    /// Dates enregistrées en UTC (le Clock de l'application est en UTC).
    private LocalDateTime maintenant() {
        return LocalDateTime.now(clock);
    }
}