package fr.mecadetect.intervention.application;

import fr.mecadetect.intervention.domain.HistoriqueStatut;
import fr.mecadetect.intervention.domain.Intervention;

import java.util.List;

/// Une intervention et son historique, entièrement chargés pour être convertis hors transaction.
///
/// @param intervention l'intervention, avec son équipement, son demandeur et son technicien
/// @param historique   changements de statut, du plus ancien au plus récent
public record DetailIntervention(Intervention intervention, List<HistoriqueStatut> historique) {
}