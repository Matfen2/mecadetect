package fr.mecadetect.utilisateur;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/// Accès aux comptes utilisateurs. L'email est unique sans tenir compte de la casse.
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /// Recherche un compte par email, sans tenir compte de la casse.
    ///
    /// @param email email saisi
    /// @return le compte, ou vide s'il n'existe pas
    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    /// Vérifie si un email est déjà utilisé, sans tenir compte de la casse.
    ///
    /// @param email email à tester
    /// @return vrai si un compte utilise déjà cet email
    boolean existsByEmailIgnoreCase(String email);

    /// Comptes actifs d'un rôle, par ordre alphabétique du nom puis du prénom.
    ///
    /// @param role rôle recherché
    /// @return les comptes actifs de ce rôle
    List<Utilisateur> findByRoleAndActifTrueOrderByNomAscPrenomAsc(Role role);
}