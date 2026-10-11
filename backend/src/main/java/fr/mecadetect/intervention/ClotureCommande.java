package fr.mecadetect.intervention;

/** Clôture saisie par le technicien, avec la version qu'il a sous les yeux. */
public record ClotureCommande(long version, String compteRendu, int tempsPasseMin) {
}