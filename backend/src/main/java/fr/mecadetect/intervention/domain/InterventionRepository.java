package fr.mecadetect.intervention.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// Port de persistance des interventions : ce dont le domaine et les cas d'usage ont besoin,
/// exprimé dans leur vocabulaire.
///
/// L'implémentation est un adaptateur de l'infrastructure (Spring Data JPA et SQL Oracle) :
/// le service ne connaît ni JPA, ni les requêtes, ni la séquence Oracle. Seules les classes de
/// pagination de Spring Data (`Page`, `Pageable`) traversent la frontière, par choix pragmatique.
public interface InterventionRepository {

    /// Enregistre une nouvelle intervention.
    ///
    /// @param intervention intervention à enregistrer
    /// @return l'intervention enregistrée, avec son identifiant
    Intervention enregistrer(Intervention intervention);

    /// Enregistre l'intervention et écrit immédiatement en base : la version (verrou optimiste)
    /// est contrôlée tout de suite, et les requêtes suivantes voient le nouveau statut.
    ///
    /// @param intervention intervention modifiée
    /// @return l'intervention enregistrée
    Intervention enregistrerEtSynchroniser(Intervention intervention);

    /// Intervention avec tout ce que le détail affiche : équipement, type, zone, demandeur, technicien.
    ///
    /// @param idIntervention identifiant de l'intervention
    /// @return l'intervention, ou vide si elle n'existe pas
    Optional<Intervention> trouverDetail(Long idIntervention);

    /// Numéro métier lisible et unique, par exemple INT-2026-000123.
    ///
    /// @param annee année de la demande
    /// @return le prochain numéro
    String prochainNumero(int annee);

    /// RG-04 : le technicien a-t-il déjà une intervention dans ce statut ?
    ///
    /// @param idTechnicien identifiant du technicien
    /// @param statut       statut recherché
    /// @return vrai si au moins une de ses interventions est dans ce statut
    boolean existePourTechnicien(Long idTechnicien, StatutIntervention statut);

    /// RG-05 : l'équipement a-t-il une intervention dans ce statut ?
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @param statut       statut recherché
    /// @return vrai si au moins une intervention de l'équipement est dans ce statut
    boolean existePourEquipement(Long idEquipement, StatutIntervention statut);

    /// RG-05 : l'équipement a-t-il une intervention dans l'un de ces statuts ?
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @param statuts      statuts recherchés, en général les statuts ouverts
    /// @return vrai si au moins une intervention de l'équipement est dans l'un de ces statuts
    boolean existePourEquipement(Long idEquipement, Collection<StatutIntervention> statuts);

    /// Interventions à traiter par un technicien : celle EN_COURS d'abord, puis par date planifiée.
    ///
    /// @param idTechnicien identifiant du technicien
    /// @param statuts      statuts retenus
    /// @param pageable     page et taille demandées
    /// @return la page de résumés d'interventions
    Page<InterventionResume> aTraiterPar(Long idTechnicien, Collection<StatutIntervention> statuts, Pageable pageable);

    /// Recherche paginée ; un paramètre null désactive son filtre.
    ///
    /// @param statut       statut recherché
    /// @param priorite     priorité recherchée
    /// @param idTechnicien identifiant du technicien affecté
    /// @param idEquipement identifiant de l'équipement
    /// @param depuis       début de la période de demande, inclus
    /// @param avant        fin de la période de demande, exclue
    /// @param pageable     page, taille et tri demandés
    /// @return la page de résumés d'interventions
    Page<InterventionResume> rechercher(StatutIntervention statut, Priorite priorite, Long idTechnicien,
                                        Long idEquipement, LocalDateTime depuis, LocalDateTime avant,
                                        Pageable pageable);

    /// Nombre d'interventions dans ces statuts, par technicien affecté.
    ///
    /// @param statuts statuts retenus
    /// @return une ligne par technicien concerné ; un technicien sans intervention n'apparaît pas
    List<ChargeTechnicien> chargeParTechnicien(Collection<StatutIntervention> statuts);

    /// Interventions EN_COURS, au plus une par technicien (RG-04).
    ///
    /// @return les interventions en cours, avec leur équipement et leur technicien
    List<InterventionResume> enCours();
}
