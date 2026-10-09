package fr.mecadetect.equipement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EquipementRepository extends JpaRepository<Equipement, Long>,
        JpaSpecificationExecutor<Equipement> {

    boolean existsByReferenceIgnoreCase(String reference);

    /**
     * Charge l'équipement avec sa zone et son type en une seule requête (jointures).
     * Nécessaire car open-in-view est désactivé : la conversion en DTO a lieu hors
     * transaction, un accès à une association LAZY non chargée échouerait.
     */
    @EntityGraph(attributePaths = {"type", "zone"})
    @Query("select e from Equipement e where e.id = :id")
    Optional<Equipement> trouverAvecTypeEtZone(@Param("id") Long id);

    /**
     * Recherche paginée : la zone et le type sont chargés par jointure dans la même
     * requête que la liste. Sans cet EntityGraph, afficher 20 équipements coûterait
     * jusqu'à 41 requêtes (problème N+1). La requête de comptage n'est pas affectée.
     */
    @Override
    @EntityGraph(attributePaths = {"type", "zone"})
    Page<Equipement> findAll(Specification<Equipement> specification, Pageable pageable);
}