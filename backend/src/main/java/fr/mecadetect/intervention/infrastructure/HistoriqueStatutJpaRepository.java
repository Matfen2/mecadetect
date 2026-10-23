package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.HistoriqueStatut;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/// Repository Spring Data de l'historique des statuts, alimenté par le trigger de la migration V3.
/// Utilisé par `HistoriqueStatutRepositoryAdapter`.
public interface HistoriqueStatutJpaRepository extends JpaRepository<HistoriqueStatut, Long> {

    /// Historique d'une intervention ; l'auteur est chargé dans la même requête (pas de N+1).
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @return les changements de statut, du plus ancien au plus récent
    @EntityGraph(attributePaths = "auteur")
    List<HistoriqueStatut> findByInterventionIdOrderByDateChangementAscIdAsc(Long idIntervention);
}