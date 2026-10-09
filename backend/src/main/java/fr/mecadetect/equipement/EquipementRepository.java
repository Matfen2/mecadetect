package fr.mecadetect.equipement;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EquipementRepository extends JpaRepository<Equipement, Long> {

    boolean existsByReferenceIgnoreCase(String reference);

    /**
     * Charge l'équipement avec sa zone et son type en une seule requête (jointures).
     * Nécessaire car open-in-view est désactivé : la conversion en DTO a lieu hors
     * transaction, un accès à une association LAZY non chargée échouerait.
     */
    @EntityGraph(attributePaths = {"type", "zone"})
    @Query("select e from Equipement e where e.id = :id")
    Optional<Equipement> trouverAvecTypeEtZone(@Param("id") Long id);
}