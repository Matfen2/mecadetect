package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.ChargeTechnicien;
import fr.mecadetect.intervention.domain.Intervention;
import fr.mecadetect.intervention.domain.InterventionResume;
import fr.mecadetect.intervention.domain.StatutIntervention;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// Repository Spring Data des interventions, utilisé par `InterventionRepositoryAdapter`
/// pour implémenter le port [fr.mecadetect.intervention.domain.InterventionRepository].
///
/// La recherche paginée avec filtres optionnels est dans le fragment [InterventionRechercheRepository] :
/// elle est construite dynamiquement pour que chaque combinaison de filtres puisse utiliser un index.
public interface InterventionJpaRepository extends JpaRepository<Intervention, Long>, InterventionRechercheRepository {

    /// Valeur suivante de la séquence Oracle (migration V2).
    ///
    /// @return la valeur suivante de `seq_numero_intervention`
    @Query(value = "SELECT seq_numero_intervention.NEXTVAL FROM dual", nativeQuery = true)
    Number valeurSuivanteSequence();

    /// Numéro métier lisible, par exemple INT-2026-000123.
    ///
    /// @param annee année de la demande
    /// @return le numéro, unique grâce à la séquence Oracle
    default String prochainNumero(int annee) {
        return "INT-%d-%06d".formatted(annee, valeurSuivanteSequence().longValue());
    }

    /// Intervention avec tout ce que le détail affiche, en une seule requête.
    ///
    /// @param id identifiant de l'intervention
    /// @return l'intervention avec son équipement, son type, sa zone, son demandeur et son technicien,
    ///         ou vide si elle n'existe pas
    @EntityGraph(attributePaths = {"equipement", "equipement.type", "equipement.zone", "demandeur", "technicien"})
    @Query("select i from Intervention i where i.id = :id")
    Optional<Intervention> trouverDetail(@Param("id") Long id);

    /// Interventions à traiter par un technicien : celle EN_COURS d'abord, puis par date planifiée.
    ///
    /// @param idTechnicien identifiant du technicien
    /// @param statuts      statuts retenus
    /// @param pageable     page et taille demandées ; le tri est fixé par la requête
    /// @return la page de résumés d'interventions
    @Query(value = """
            select new fr.mecadetect.intervention.domain.InterventionResume(
                i.id, i.numero, i.titre, i.priorite, i.statut, i.dateDemande, i.datePlanifiee,
                e.reference, e.nom, t.id, t.prenom, t.nom)
            from Intervention i
            join i.equipement e
            join i.technicien t
            where t.id = :idTechnicien
              and i.statut in :statuts
            order by case when i.statut = fr.mecadetect.intervention.domain.StatutIntervention.EN_COURS then 0 else 1 end,
                     i.datePlanifiee, i.id
            """,
            countQuery = """
            select count(i) from Intervention i
            where i.technicien.id = :idTechnicien and i.statut in :statuts
            """)
    Page<InterventionResume> aTraiterPar(@Param("idTechnicien") Long idTechnicien,
                                         @Param("statuts") Collection<StatutIntervention> statuts,
                                         Pageable pageable);

    /// RG-04 : le technicien a-t-il déjà une intervention dans ce statut ?
    ///
    /// @param idTechnicien identifiant du technicien
    /// @param statut       statut recherché
    /// @return vrai si au moins une de ses interventions est dans ce statut
    boolean existsByTechnicienIdAndStatut(Long idTechnicien, StatutIntervention statut);

    /// RG-05 : l'équipement a-t-il une intervention dans ce statut ?
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @param statut       statut recherché
    /// @return vrai si au moins une intervention de l'équipement est dans ce statut
    boolean existsByEquipementIdAndStatut(Long idEquipement, StatutIntervention statut);

    /// RG-05 : l'équipement a-t-il encore une intervention dans l'un de ces statuts ?
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @param statuts      statuts recherchés, en général les statuts ouverts
    /// @return vrai si au moins une intervention de l'équipement est dans l'un de ces statuts
    boolean existsByEquipementIdAndStatutIn(Long idEquipement, Collection<StatutIntervention> statuts);

    /// Nombre d'interventions dans ces statuts, par technicien affecté.
    ///
    /// @param statuts statuts retenus
    /// @return une ligne par technicien concerné ; un technicien sans intervention n'apparaît pas
    @Query("""
            select new fr.mecadetect.intervention.domain.ChargeTechnicien(i.technicien.id, count(i))
            from Intervention i
            where i.technicien is not null and i.statut in :statuts
            group by i.technicien.id
            """)
    List<ChargeTechnicien> chargeParTechnicien(@Param("statuts") Collection<StatutIntervention> statuts);

    /// Interventions EN_COURS, au plus une par technicien (RG-04), projetées en résumé.
    ///
    /// @return les interventions en cours, avec leur équipement et leur technicien
    @Query("""
            select new fr.mecadetect.intervention.domain.InterventionResume(
                i.id, i.numero, i.titre, i.priorite, i.statut, i.dateDemande, i.datePlanifiee,
                e.reference, e.nom, t.id, t.prenom, t.nom)
            from Intervention i
            join i.equipement e
            join i.technicien t
            where i.statut = fr.mecadetect.intervention.domain.StatutIntervention.EN_COURS
            """)
    List<InterventionResume> enCours();
}