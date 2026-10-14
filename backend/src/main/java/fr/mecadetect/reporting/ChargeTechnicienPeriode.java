package fr.mecadetect.reporting;

/** Charge d'un technicien : interventions ouvertes aujourd'hui, travail terminé sur la période. */
public record ChargeTechnicienPeriode(long idTechnicien, String nomComplet, long ouvertes,
                                      long terminees, long minutesPassees) {
}