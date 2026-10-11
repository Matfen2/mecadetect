package fr.mecadetect.intervention;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, Long> {

    List<HistoriqueStatut> findByInterventionIdOrderByDateChangementAscIdAsc(Long idIntervention);
}