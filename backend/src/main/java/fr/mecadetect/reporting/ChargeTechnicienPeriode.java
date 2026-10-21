package fr.mecadetect.reporting;

/// Charge d'un technicien : interventions ouvertes aujourd'hui, travail terminé sur la période.
///
/// @param idTechnicien   identifiant du technicien
/// @param prenom         prénom du technicien
/// @param nom            nom du technicien
/// @param ouvertes       interventions ouvertes qui lui sont affectées, à cet instant
/// @param terminees      interventions terminées sur la période
/// @param minutesPassees temps passé sur les interventions terminées de la période, en minutes
public record ChargeTechnicienPeriode(long idTechnicien, String prenom, String nom, long ouvertes,
                                      long terminees, long minutesPassees) {

    /// Nom complet du technicien, pour l'affichage.
    ///
    /// @return le prénom suivi du nom
    public String nomComplet() {
        return prenom + " " + nom;
    }
}