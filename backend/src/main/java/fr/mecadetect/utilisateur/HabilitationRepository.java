package fr.mecadetect.utilisateur;

import org.springframework.data.jpa.repository.JpaRepository;

/** RG-09 : existsById(new HabilitationId(idType, idTechnicien)) suffit à vérifier une habilitation. */
public interface HabilitationRepository extends JpaRepository<Habilitation, HabilitationId> {
}