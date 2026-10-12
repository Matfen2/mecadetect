package fr.mecadetect.utilisateur;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Comptes actifs d'un rôle, par ordre alphabétique. */
    List<Utilisateur> findByRoleAndActifTrueOrderByNomAscPrenomAsc(Role role);
}