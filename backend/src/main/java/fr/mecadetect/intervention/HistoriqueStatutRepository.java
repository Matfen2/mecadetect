package fr.mecadetect.intervention;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, Long> {

    /** L'auteur est chargé dans la même requête : pas de N+1 sur l'historique. */
    @EntityGraph(attributePaths = "auteur")
    List<HistoriqueStatut> findByInterventionIdOrderByDateChangementAscIdAsc(Long idIntervention);
}