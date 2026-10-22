package fr.mecadetect.maintenance;

/// Ce que PKG_MAINTENANCE.ARCHIVER_INTERVENTIONS a déplacé vers les tables d'archive.
///
/// @param interventions nombre d'interventions archivées
/// @param historiques   nombre de lignes d'historique archivées
public record ResultatArchivage(long interventions, long historiques) {
}