package fr.mecadetect.intervention.domain;

import fr.mecadetect.utilisateur.Utilisateur;

import java.util.Optional;

/// Port vers les comptes utilisateurs : auteurs des actions et techniciens assignés.
public interface Utilisateurs {

    /// @param idUtilisateur identifiant de l'utilisateur
    /// @return l'utilisateur, actif ou non, ou vide s'il n'existe pas
    Optional<Utilisateur> trouver(Long idUtilisateur);
}
