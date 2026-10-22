package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.InterventionResume;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.domain.StatutIntervention;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/// Recherche paginée des interventions (UC-DB5), construite dynamiquement.
/// Fragment de [InterventionJpaRepository] : Spring Data fournit l'implémentation
/// InterventionRechercheRepositoryImpl, détectée par son nom.
public interface InterventionRechercheRepository {

    /// Recherche paginée ; un paramètre null désactive son filtre.
    ///
    /// @param statut       statut recherché
    /// @param priorite     priorité recherchée
    /// @param idTechnicien identifiant du technicien affecté
    /// @param idEquipement identifiant de l'équipement
    /// @param depuis       début de la période de demande, inclus
    /// @param avant        fin de la période de demande, exclue
    /// @param pageable     page, taille et tri demandés
    /// @return la page de résumés d'interventions
    Page<InterventionResume> rechercher(StatutIntervention statut, Priorite priorite, Long idTechnicien,
                                        Long idEquipement, LocalDateTime depuis, LocalDateTime avant,
                                        Pageable pageable);
}