package fr.mecadetect.intervention;

import java.util.List;

/** Une intervention et son historique, entièrement chargés pour être convertis hors transaction. */
public record DetailIntervention(Intervention intervention, List<HistoriqueStatut> historique) {
}