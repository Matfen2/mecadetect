package fr.mecadetect.equipement.importation;

import fr.mecadetect.equipement.Equipement;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

import java.util.ArrayList;
import java.util.List;

/// Écouteur des lignes ignorées (skip) par le step d'import : chaque rejet est rangé, avec son
/// motif, dans le contexte d'exécution du step, où le service le relit pour bâtir le rapport.
///
/// Sans état : le contexte appartient à l'exécution en cours, un même écouteur sert tous les imports.
public class CollecteurRejets implements SkipListener<LigneEquipementCsv, Equipement> {

    /// Clé des rejets dans le contexte d'exécution du step.
    public static final String CLE_REJETS = "rejets";

    /// Au-delà, les rejets sont comptés mais pas détaillés : le rapport reste lisible.
    static final int REJETS_DETAILLES_MAX = 100;

    /// Ligne illisible : elle n'a pas pu être découpée en colonnes.
    ///
    /// @param erreur erreur de lecture
    @Override
    public void onSkipInRead(Throwable erreur) {
        if (erreur instanceof FlatFileParseException illisible) {
            enregistrer(new RejetImport(illisible.getLineNumber(),
                    "Ligne illisible : 5 colonnes séparées par des points-virgules sont attendues."));
        } else {
            enregistrer(new RejetImport(0, "Ligne illisible : " + erreur.getMessage()));
        }
    }

    /// Ligne refusée par la validation.
    ///
    /// @param ligne  ligne rejetée
    /// @param erreur motif du rejet
    @Override
    public void onSkipInProcess(LigneEquipementCsv ligne, Throwable erreur) {
        enregistrer(new RejetImport(ligne.numeroLigne(), erreur.getMessage()));
    }

    /// Équipement refusé par la base au moment de l'écriture (contrainte d'unicité, par exemple
    /// si un autre utilisateur vient de créer la même référence).
    ///
    /// @param equipement équipement rejeté
    /// @param erreur     erreur de la base
    @Override
    public void onSkipInWrite(Equipement equipement, Throwable erreur) {
        enregistrer(new RejetImport(0, "La référence « " + equipement.getReference()
                + " » a été refusée par la base de données (déjà utilisée ?)."));
    }

    private static void enregistrer(RejetImport rejet) {
        var contexte = StepSynchronizationManager.getContext().getStepExecution().getExecutionContext();
        @SuppressWarnings("unchecked")
        var rejets = (List<RejetImport>) contexte.get(CLE_REJETS);
        if (rejets == null) {
            rejets = new ArrayList<>();
            contexte.put(CLE_REJETS, rejets);
        }
        if (rejets.size() < REJETS_DETAILLES_MAX) {
            rejets.add(rejet);
        }
    }
}
