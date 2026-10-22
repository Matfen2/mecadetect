package fr.mecadetect.intervention.application;

/// Clôture saisie par le technicien, avec la version qu'il a sous les yeux.
///
/// @param version       version de l'intervention connue du technicien (verrou optimiste)
/// @param compteRendu   compte rendu de l'intervention, obligatoire
/// @param tempsPasseMin temps passé, en minutes, strictement positif
public record ClotureCommande(long version, String compteRendu, int tempsPasseMin) {
}