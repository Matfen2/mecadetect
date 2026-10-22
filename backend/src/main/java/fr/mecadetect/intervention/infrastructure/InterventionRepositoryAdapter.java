package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.ChargeTechnicien;
import fr.mecadetect.intervention.domain.Intervention;
import fr.mecadetect.intervention.domain.InterventionRepository;
import fr.mecadetect.intervention.domain.InterventionResume;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.domain.StatutIntervention;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// Adaptateur : implémente le port [InterventionRepository] avec Spring Data JPA.
@Repository
class InterventionRepositoryAdapter implements InterventionRepository {

    private final InterventionJpaRepository jpa;

    InterventionRepositoryAdapter(InterventionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Intervention enregistrer(Intervention intervention) {
        return jpa.save(intervention);
    }

    @Override
    public Intervention enregistrerEtSynchroniser(Intervention intervention) {
        return jpa.saveAndFlush(intervention);
    }

    @Override
    public Optional<Intervention> trouverDetail(Long idIntervention) {
        return jpa.trouverDetail(idIntervention);
    }

    @Override
    public String prochainNumero(int annee) {
        return jpa.prochainNumero(annee);
    }

    @Override
    public boolean existePourTechnicien(Long idTechnicien, StatutIntervention statut) {
        return jpa.existsByTechnicienIdAndStatut(idTechnicien, statut);
    }

    @Override
    public boolean existePourEquipement(Long idEquipement, StatutIntervention statut) {
        return jpa.existsByEquipementIdAndStatut(idEquipement, statut);
    }

    @Override
    public boolean existePourEquipement(Long idEquipement, Collection<StatutIntervention> statuts) {
        return jpa.existsByEquipementIdAndStatutIn(idEquipement, statuts);
    }

    @Override
    public Page<InterventionResume> aTraiterPar(Long idTechnicien, Collection<StatutIntervention> statuts,
                                                Pageable pageable) {
        return jpa.aTraiterPar(idTechnicien, statuts, pageable);
    }

    @Override
    public Page<InterventionResume> rechercher(StatutIntervention statut, Priorite priorite, Long idTechnicien,
                                               Long idEquipement, LocalDateTime depuis, LocalDateTime avant,
                                               Pageable pageable) {
        return jpa.rechercher(statut, priorite, idTechnicien, idEquipement, depuis, avant, pageable);
    }

    @Override
    public List<ChargeTechnicien> chargeParTechnicien(Collection<StatutIntervention> statuts) {
        return jpa.chargeParTechnicien(statuts);
    }

    @Override
    public List<InterventionResume> enCours() {
        return jpa.enCours();
    }
}
