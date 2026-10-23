package fr.mecadetect.referentiel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/// Accès aux zones d'implantation du référentiel.
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    /// Toutes les zones, pour les listes de choix.
    ///
    /// @return les zones, par ordre alphabétique du code
    List<Zone> findAllByOrderByCodeAsc();

    /// Vérifie si un code de zone est déjà utilisé, sans tenir compte de la casse.
    ///
    /// @param code code à tester
    /// @return vrai si une zone porte déjà ce code
    boolean existsByCodeIgnoreCase(String code);
}