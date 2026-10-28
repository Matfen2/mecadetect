package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.HistoriqueStatut;
import fr.mecadetect.intervention.domain.HistoriqueStatutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/// Adaptateur : implémente le port [HistoriqueStatutRepository] avec Spring Data JPA.
@Repository
@RequiredArgsConstructor
class HistoriqueStatutRepositoryAdapter implements HistoriqueStatutRepository {

    private final HistoriqueStatutJpaRepository jpa;

    @Override
    public List<HistoriqueStatut> historiqueDe(Long idIntervention) {
        return jpa.findByInterventionIdOrderByDateChangementAscIdAsc(idIntervention);
    }
}
