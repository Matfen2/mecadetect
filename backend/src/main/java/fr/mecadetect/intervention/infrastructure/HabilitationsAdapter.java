package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.Habilitations;
import fr.mecadetect.utilisateur.HabilitationId;
import fr.mecadetect.utilisateur.HabilitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// Adaptateur : implémente le port [Habilitations] avec le repository Spring Data des habilitations.
@Repository
@RequiredArgsConstructor
class HabilitationsAdapter implements Habilitations {

    private final HabilitationRepository habilitationRepository;

    @Override
    public boolean estHabilite(Long idType, Long idTechnicien) {
        return habilitationRepository.existsById(new HabilitationId(idType, idTechnicien));
    }
}
