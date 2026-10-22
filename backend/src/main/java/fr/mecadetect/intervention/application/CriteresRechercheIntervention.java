package fr.mecadetect.intervention.application;

import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.domain.StatutIntervention;

import java.time.LocalDate;

/// Filtres de la recherche d'interventions ; un critère null est ignoré.
///
/// @param statut       statut recherché
/// @param priorite     priorité recherchée
/// @param idTechnicien identifiant du technicien affecté
/// @param depuis       premier jour de la période de demande, inclus
/// @param jusqua       dernier jour de la période de demande, inclus
public record CriteresRechercheIntervention(
        StatutIntervention statut,
        Priorite priorite,
        Long idTechnicien,
        LocalDate depuis,
        LocalDate jusqua) {
}