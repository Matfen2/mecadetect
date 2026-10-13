package fr.mecadetect.intervention;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Recherche paginée des interventions (UC-DB5), construite dynamiquement.
 * Fragment de InterventionRepository : Spring Data fournit l'implémentation
 * InterventionRechercheRepositoryImpl, détectée par son nom.
 */
public interface InterventionRechercheRepository {

    /** Un paramètre null désactive son filtre ; [depuis ; avant[ porte sur la date de demande. */
    Page<InterventionResume> rechercher(StatutIntervention statut, Priorite priorite, Long idTechnicien,
                                        Long idEquipement, LocalDateTime depuis, LocalDateTime avant,
                                        Pageable pageable);
}