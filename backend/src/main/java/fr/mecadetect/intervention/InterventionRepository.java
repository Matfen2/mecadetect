package fr.mecadetect.intervention;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    /** Valeur suivante de la séquence Oracle (migration V2). */
    @Query(value = "SELECT seq_numero_intervention.NEXTVAL FROM dual", nativeQuery = true)
    Number valeurSuivanteSequence();

    /** Numéro métier lisible, par exemple INT-2026-000123. */
    default String prochainNumero(int annee) {
        return "INT-%d-%06d".formatted(annee, valeurSuivanteSequence().longValue());
    }

    /** Intervention avec tout ce que le détail affiche, en une seule requête. */
    @EntityGraph(attributePaths = {"equipement", "equipement.type", "equipement.zone", "demandeur", "technicien"})
    @Query("select i from Intervention i where i.id = :id")
    Optional<Intervention> trouverDetail(@Param("id") Long id);

    /** RG-04 : le technicien a-t-il déjà une intervention dans ce statut ? */
    boolean existsByTechnicienIdAndStatut(Long idTechnicien, StatutIntervention statut);

    /** RG-05 : l'équipement a-t-il encore une intervention ouverte ? */
    boolean existsByEquipementIdAndStatutIn(Long idEquipement, Collection<StatutIntervention> statuts);
}