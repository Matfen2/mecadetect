package fr.mecadetect.intervention;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    /**
     * Recherche paginée, projetée en DTO (UC-DB5). Un paramètre null désactive son filtre.
     * Le tri vient du Pageable ; la requête de comptage évite la projection, inutile pour compter.
     */
    @Query(value = """
            select new fr.mecadetect.intervention.InterventionResume(
                i.id, i.numero, i.titre, i.priorite, i.statut, i.dateDemande, i.datePlanifiee,
                e.reference, e.nom, t.id, t.prenom, t.nom)
            from Intervention i
            join i.equipement e
            left join i.technicien t
            where (:statut is null or i.statut = :statut)
              and (:priorite is null or i.priorite = :priorite)
              and (:idTechnicien is null or t.id = :idTechnicien)
              and (:idEquipement is null or e.id = :idEquipement)
              and (:depuis is null or i.dateDemande >= :depuis)
              and (:avant is null or i.dateDemande < :avant)
            """,
            countQuery = """
            select count(i)
            from Intervention i
            join i.equipement e
            left join i.technicien t
            where (:statut is null or i.statut = :statut)
              and (:priorite is null or i.priorite = :priorite)
              and (:idTechnicien is null or t.id = :idTechnicien)
              and (:idEquipement is null or e.id = :idEquipement)
              and (:depuis is null or i.dateDemande >= :depuis)
              and (:avant is null or i.dateDemande < :avant)
            """)
    Page<InterventionResume> rechercher(@Param("statut") StatutIntervention statut,
                                        @Param("priorite") Priorite priorite,
                                        @Param("idTechnicien") Long idTechnicien,
                                        @Param("idEquipement") Long idEquipement,
                                        @Param("depuis") LocalDateTime depuis,
                                        @Param("avant") LocalDateTime avant,
                                        Pageable pageable);

    /** Interventions à traiter par un technicien : celle EN_COURS d'abord, puis par date planifiée. */
    @Query(value = """
            select new fr.mecadetect.intervention.InterventionResume(
                i.id, i.numero, i.titre, i.priorite, i.statut, i.dateDemande, i.datePlanifiee,
                e.reference, e.nom, t.id, t.prenom, t.nom)
            from Intervention i
            join i.equipement e
            join i.technicien t
            where t.id = :idTechnicien
              and i.statut in :statuts
            order by case when i.statut = fr.mecadetect.intervention.StatutIntervention.EN_COURS then 0 else 1 end,
                     i.datePlanifiee, i.id
            """,
            countQuery = """
            select count(i) from Intervention i
            where i.technicien.id = :idTechnicien and i.statut in :statuts
            """)
    Page<InterventionResume> aTraiterPar(@Param("idTechnicien") Long idTechnicien,
                                         @Param("statuts") Collection<StatutIntervention> statuts,
                                         Pageable pageable);

    /** RG-04 : le technicien a-t-il déjà une intervention dans ce statut ? */
    boolean existsByTechnicienIdAndStatut(Long idTechnicien, StatutIntervention statut);

    /** RG-05 : l'équipement a-t-il une intervention dans ce statut ? */
    boolean existsByEquipementIdAndStatut(Long idEquipement, StatutIntervention statut);

    /** RG-05 : l'équipement a-t-il encore une intervention ouverte ? */
    boolean existsByEquipementIdAndStatutIn(Long idEquipement, Collection<StatutIntervention> statuts);

    /** Nombre d'interventions dans ces statuts, par technicien assigné (une ligne par technicien concerné). */
    @Query("""
            select new fr.mecadetect.intervention.ChargeTechnicien(i.technicien.id, count(i))
            from Intervention i
            where i.technicien is not null and i.statut in :statuts
            group by i.technicien.id
            """)
    List<ChargeTechnicien> chargeParTechnicien(@Param("statuts") Collection<StatutIntervention> statuts);

    /** Interventions EN_COURS, au plus une par technicien (RG-04), projetées en résumé. */
    @Query("""
            select new fr.mecadetect.intervention.InterventionResume(
                i.id, i.numero, i.titre, i.priorite, i.statut, i.dateDemande, i.datePlanifiee,
                e.reference, e.nom, t.id, t.prenom, t.nom)
            from Intervention i
            join i.equipement e
            join i.technicien t
            where i.statut = fr.mecadetect.intervention.StatutIntervention.EN_COURS
            """)
    List<InterventionResume> enCours();
}