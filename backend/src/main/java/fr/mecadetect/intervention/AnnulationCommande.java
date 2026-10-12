package fr.mecadetect.intervention;

/** Annulation décidée par l'admin, avec la version qu'il a sous les yeux. */
public record AnnulationCommande(long version, String motif) {
}