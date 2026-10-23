package fr.mecadetect.intervention.domain;

import java.util.List;

/// Port de lecture de l'historique des statuts. L'historique est écrit par un trigger Oracle
/// (migration V3) : le domaine ne fait que le lire.
public interface HistoriqueStatutRepository {

    /// Historique d'une intervention, avec l'auteur de chaque changement.
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @return les changements de statut, du plus ancien au plus récent
    List<HistoriqueStatut> historiqueDe(Long idIntervention);
}
