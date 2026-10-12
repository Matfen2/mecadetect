package fr.mecadetect.intervention;

import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.utilisateur.Utilisateur;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Les transitions possibles du cycle de vie d'une intervention.
 * L'interface est scellée : la liste est fermée, et le compilateur vérifie
 * que chaque switch sur une transition traite tous les cas.
 * Chaque record porte les données de sa transition et vérifie leurs règles (RG-03).
 */
public sealed interface Transition {

    record Planification(Utilisateur technicien, LocalDateTime datePlanifiee) implements Transition {
        public Planification {
            Objects.requireNonNull(technicien, "technicien");
            Objects.requireNonNull(datePlanifiee, "datePlanifiee");
        }
    }

    record Demarrage(LocalDateTime dateDebut) implements Transition {
        public Demarrage {
            Objects.requireNonNull(dateDebut, "dateDebut");
        }
    }

    record Cloture(String compteRendu, int tempsPasseMin, LocalDateTime dateFin) implements Transition {
        public Cloture {
            if (compteRendu == null || compteRendu.isBlank()) {
                throw new RegleMetierException("COMPTE_RENDU_OBLIGATOIRE",
                        "La clôture d'une intervention exige un compte rendu.");
            }
            if (tempsPasseMin <= 0) {
                throw new RegleMetierException("TEMPS_PASSE_INVALIDE",
                        "Le temps passé doit être supérieur à 0 minute.");
            }
            Objects.requireNonNull(dateFin, "dateFin");
            compteRendu = compteRendu.trim();
        }
    }

    record Annulation(String motif) implements Transition {
        public Annulation {
            if (motif == null || motif.isBlank()) {
                throw new RegleMetierException("MOTIF_OBLIGATOIRE",
                        "L'annulation d'une intervention exige un motif.");
            }
            motif = motif.trim();
        }
    }
}