package fr.mecadetect.intervention.domain;

import fr.mecadetect.equipement.Equipement;

import java.util.Optional;

/// Port vers le parc d'équipements, tel que les cas d'usage des interventions en ont besoin.
public interface ParcEquipements {

    /// Équipement avec son type et sa zone, chargés ensemble.
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @return l'équipement, ou vide s'il n'existe pas
    Optional<Equipement> trouverAvecTypeEtZone(Long idEquipement);

    /// @param idEquipement identifiant de l'équipement
    /// @return vrai si l'équipement existe
    boolean existe(Long idEquipement);

    /// Écrit immédiatement en base le nouveau statut des équipements modifiés.
    void synchroniser();
}
