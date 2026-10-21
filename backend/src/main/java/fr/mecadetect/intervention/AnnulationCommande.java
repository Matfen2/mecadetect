package fr.mecadetect.intervention;

/// Annulation décidée par l'admin, avec la version qu'il a sous les yeux.
///
/// @param version version de l'intervention connue de l'admin (verrou optimiste)
/// @param motif   motif de l'annulation, obligatoire
public record AnnulationCommande(long version, String motif) {
}