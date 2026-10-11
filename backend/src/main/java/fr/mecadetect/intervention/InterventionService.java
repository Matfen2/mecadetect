package fr.mecadetect.intervention;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.erreur.DonneesInvalidesException;
import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.erreur.RessourceIntrouvableException;
import fr.mecadetect.utilisateur.HabilitationId;
import fr.mecadetect.utilisateur.HabilitationRepository;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Cas d'usage des interventions. Les règles propres à une intervention sont dans l'entité ;
 * le service porte celles qui ont besoin de la base (équipement, utilisateurs, habilitations).
 */
@Service
@Transactional
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final EquipementRepository equipementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final HabilitationRepository habilitationRepository;
    private final Clock clock;

    public InterventionService(InterventionRepository interventionRepository,
                               HistoriqueStatutRepository historiqueRepository,
                               EquipementRepository equipementRepository,
                               UtilisateurRepository utilisateurRepository,
                               HabilitationRepository habilitationRepository,
                               Clock clock) {
        this.interventionRepository = interventionRepository;
        this.historiqueRepository = historiqueRepository;
        this.equipementRepository = equipementRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.habilitationRepository = habilitationRepository;
        this.clock = clock;
    }

    /** Déclaration d'une panne : intervention DEMANDEE et équipement EN_PANNE (RG-06, RG-07). */
    public DetailIntervention declarer(DeclarationCommande commande, Long idAuteur) {
        var auteur = chargerUtilisateur(idAuteur);
        var equipement = chargerEquipement(commande.idEquipement());
        var maintenant = maintenant();

        var intervention = new Intervention(
                interventionRepository.prochainNumero(maintenant.getYear()),
                commande.titre(), commande.description(), commande.priorite(),
                equipement, auteur, maintenant);
        equipement.signalerPanne();

        return detail(interventionRepository.save(intervention));
    }

    /** DEMANDEE → PLANIFIEE : l'admin assigne un technicien habilité (RG-01, RG-09). */
    public DetailIntervention planifier(Long idIntervention, PlanificationCommande commande, Long idAuteur) {
        var auteur = chargerUtilisateur(idAuteur);
        var intervention = chargerIntervention(idIntervention);
        verifierVersion(intervention, commande.version());

        var technicien = utilisateurRepository.findById(commande.idTechnicien())
                .orElseThrow(() -> new DonneesInvalidesException("TECHNICIEN_INCONNU",
                        "Le technicien " + commande.idTechnicien() + " n'existe pas."));
        var idType = intervention.getEquipement().getType().getId();
        if (technicien.getRole() == Role.TECHNICIEN
                && !habilitationRepository.existsById(new HabilitationId(idType, technicien.getId()))) {
            throw new RegleMetierException("TECHNICIEN_NON_HABILITE",
                    "%s %s n'est pas habilité sur les équipements de type « %s »."
                            .formatted(technicien.getPrenom(), technicien.getNom(),
                                    intervention.getEquipement().getType().getLibelle()));
        }

        intervention.planifier(technicien, commande.datePlanifiee(), auteur);
        return detail(interventionRepository.saveAndFlush(intervention));
    }

    /**
     * UC-DB6 : le client renvoie la version qu'il a affichée. Si elle a changé entre-temps,
     * quelqu'un d'autre a modifié l'intervention : on refuse plutôt que d'écraser son travail.
     * La colonne @Version protège en plus le cas de deux requêtes strictement simultanées.
     */
    private static void verifierVersion(Intervention intervention, long versionAttendue) {
        if (intervention.getVersion() != versionAttendue) {
            throw new OptimisticLockingFailureException(
                    "Version " + versionAttendue + " attendue, version " + intervention.getVersion() + " en base.");
        }
    }

    private Intervention chargerIntervention(Long idIntervention) {
        return interventionRepository.trouverDetail(idIntervention)
                .orElseThrow(() -> new RessourceIntrouvableException("INTERVENTION_INTROUVABLE",
                        "L'intervention " + idIntervention + " n'existe pas."));
    }

    private DetailIntervention detail(Intervention intervention) {
        var historique = historiqueRepository.findByInterventionIdOrderByDateChangementAscIdAsc(intervention.getId());
        return new DetailIntervention(intervention, historique);
    }

    private Equipement chargerEquipement(Long idEquipement) {
        return equipementRepository.trouverAvecTypeEtZone(idEquipement)
                .orElseThrow(() -> new RessourceIntrouvableException("EQUIPEMENT_INTROUVABLE",
                        "L'équipement " + idEquipement + " n'existe pas."));
    }

    private Utilisateur chargerUtilisateur(Long idUtilisateur) {
        return utilisateurRepository.findById(idUtilisateur)
                .filter(Utilisateur::isActif)
                .orElseThrow(() -> new ActionNonAutoriseeException("UTILISATEUR_INCONNU",
                        "Utilisateur connecté non reconnu ou désactivé."));
    }

    /** Dates enregistrées en UTC (le Clock de l'application est en UTC). */
    private LocalDateTime maintenant() {
        return LocalDateTime.now(clock);
    }
}