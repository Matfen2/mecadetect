package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.domain.ParcEquipements;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/// Adaptateur : implémente le port [ParcEquipements] avec le repository Spring Data des équipements.
@Repository
@RequiredArgsConstructor
class ParcEquipementsAdapter implements ParcEquipements {

    private final EquipementRepository equipementRepository;

    @Override
    public Optional<Equipement> trouverAvecTypeEtZone(Long idEquipement) {
        return equipementRepository.trouverAvecTypeEtZone(idEquipement);
    }

    @Override
    public boolean existe(Long idEquipement) {
        return equipementRepository.existsById(idEquipement);
    }

    @Override
    public void synchroniser() {
        equipementRepository.flush();
    }
}
