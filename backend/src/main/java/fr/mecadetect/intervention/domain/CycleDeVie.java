package fr.mecadetect.intervention.domain;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.intervention.domain.Transition.Annulation;
import fr.mecadetect.intervention.domain.Transition.Cloture;
import fr.mecadetect.intervention.domain.Transition.Demarrage;
import fr.mecadetect.intervention.domain.Transition.Planification;

import static fr.mecadetect.intervention.domain.StatutIntervention.ANNULEE;
import static fr.mecadetect.intervention.domain.StatutIntervention.DEMANDEE;
import static fr.mecadetect.intervention.domain.StatutIntervention.EN_COURS;
import static fr.mecadetect.intervention.domain.StatutIntervention.PLANIFIEE;
import static fr.mecadetect.intervention.domain.StatutIntervention.TERMINEE;

/// Machine à états du cycle de vie, en un seul endroit :
///
/// ```
/// DEMANDEE --planification--> PLANIFIEE --démarrage--> EN_COURS --clôture--> TERMINEE
///     \                           \
///      +------annulation-----------+--> ANNULEE
/// ```
///
/// Toute combinaison absente de ce switch est une transition interdite.
public final class CycleDeVie {

    private CycleDeVie() {
    }

    /// Statut obtenu en appliquant la transition au statut actuel.
    ///
    /// @param actuel     statut actuel de l'intervention
    /// @param transition transition demandée
    /// @return le nouveau statut
    /// @throws RegleMetierException si la transition est interdite depuis ce statut
    public static StatutIntervention statutApres(StatutIntervention actuel, Transition transition) {
        return switch (transition) {
            case Planification _ when actuel == DEMANDEE -> PLANIFIEE;
            case Demarrage _ when actuel == PLANIFIEE -> EN_COURS;
            case Cloture _ when actuel == EN_COURS -> TERMINEE;
            case Annulation _ when actuel == DEMANDEE || actuel == PLANIFIEE -> ANNULEE;
            default -> throw new RegleMetierException("TRANSITION_INTERDITE",
                    "Impossible d'appliquer %s à une intervention au statut %s."
                            .formatted(libelle(transition), actuel));
        };
    }

    /// Switch exhaustif sans branche default : si une transition est ajoutée
    /// à l'interface scellée, ce code ne compile plus tant qu'elle n'est pas traitée.
    static String libelle(Transition transition) {
        return switch (transition) {
            case Planification _ -> "la planification";
            case Demarrage _ -> "le démarrage";
            case Cloture _ -> "la clôture";
            case Annulation _ -> "l'annulation";
        };
    }
}