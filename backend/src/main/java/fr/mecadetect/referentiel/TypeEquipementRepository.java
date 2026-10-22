package fr.mecadetect.referentiel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/// Accès aux types d'équipement du référentiel.
public interface TypeEquipementRepository extends JpaRepository<TypeEquipement, Long> {

    /// Tous les types d'équipement, pour les listes de choix.
    ///
    /// @return les types, par ordre alphabétique du libellé
    List<TypeEquipement> findAllByOrderByLibelleAsc();

    /// Vérifie si un libellé est déjà utilisé, sans tenir compte de la casse.
    ///
    /// @param libelle libellé à tester
    /// @return vrai si un type porte déjà ce libellé
    boolean existsByLibelleIgnoreCase(String libelle);
}