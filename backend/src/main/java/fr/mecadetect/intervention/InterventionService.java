package fr.mecadetect.intervention;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.erreur.RessourceIntrouvableException;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
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
    private final Clock clock;

    public InterventionService(InterventionRepository interventionRepository,
                               HistoriqueStatutRepository historiqueRepository,
                               EquipementRepository equipementRepository,
                               UtilisateurRepository utilisateurRepository,
                               Clock clock) {
        this.interventionRepository = interventionRepository;
        this.historiqueRepository = historiqueRepository;
        this.equipementRepository = equipementRepository;
        this.utilisateurRepository = utilisateurRepository;
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