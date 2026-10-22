package fr.mecadetect.reporting;

/// Un équipement du classement des plus souvent en panne.
///
/// @param rang         rang dans le classement ; deux ex aequo partagent le même rang
/// @param reference    référence de l'équipement
/// @param nom          nom de l'équipement
/// @param nombrePannes nombre de pannes sur la période, interventions annulées exclues
public record TopEquipement(int rang, String reference, String nom, long nombrePannes) {
}