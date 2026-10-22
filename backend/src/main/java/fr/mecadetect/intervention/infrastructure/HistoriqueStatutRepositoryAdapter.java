package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.HistoriqueStatut;
import fr.mecadetect.intervention.domain.HistoriqueStatutRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/// Adaptateur : implémente le port [HistoriqueStatutRepository] avec Spring Data JPA.
@Repository
class HistoriqueStatutRepositoryAdapter implements HistoriqueStatutRepository {

    private final HistoriqueStatutJpaRepository jpa;

    HistoriqueStatutRepositoryAdapter(HistoriqueStatutJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<HistoriqueStatut> historiqueDe(Long idIntervention) {
        return jpa.findByInterventionIdOrderByDateChangementAscIdAsc(idIntervention);
    }
}
