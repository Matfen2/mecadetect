package fr.mecadetect.reporting;

/** Un équipement du classement des plus souvent en panne. Deux ex aequo partagent le même rang. */
public record TopEquipement(int rang, String reference, String nom, long nombrePannes) {
}