package fr.mecadetect.utilisateur;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** RG-09 : existsById(new HabilitationId(idType, idTechnicien)) suffit à vérifier une habilitation. */
public interface HabilitationRepository extends JpaRepository<Habilitation, HabilitationId> {

    /** Habilitations de plusieurs techniciens, types chargés dans la même requête. */
    @EntityGraph(attributePaths = "type")
    List<Habilitation> findByTechnicienIdIn(Collection<Long> idsTechniciens);
}