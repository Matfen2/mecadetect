package fr.mecadetect.demo;

import fr.mecadetect.utilisateur.Role;

/// Compte de démonstration, sans mot de passe : celui-ci est commun à tous les comptes
/// et vient de la configuration (`mecadetect.demo.mot-de-passe`).
///
/// @param nom    nom de famille
/// @param prenom prénom
/// @param email  adresse de connexion, unique
/// @param role   rôle attribué au compte
public record CompteDemo(String nom, String prenom, String email, Role role) {
}