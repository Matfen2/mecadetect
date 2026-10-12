package fr.mecadetect.intervention;

import java.time.LocalDate;

/** Filtres de la recherche d'interventions ; un critère null est ignoré. */
public record CriteresRechercheIntervention(
        StatutIntervention statut,
        Priorite priorite,
        Long idTechnicien,
        LocalDate depuis,
        LocalDate jusqua) {
}