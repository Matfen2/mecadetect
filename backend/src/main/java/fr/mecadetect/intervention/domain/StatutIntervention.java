package fr.mecadetect.intervention.domain;

import java.util.Set;

/// Statuts d'une intervention : valeurs identiques à la contrainte ck_intervention_statut.
public enum StatutIntervention {
    DEMANDEE,
    PLANIFIEE,
    EN_COURS,
    TERMINEE,
    ANNULEE;

    /// Statuts d'une intervention qui n'est pas encore terminée ni annulée.
    public static final Set<StatutIntervention> OUVERTS = Set.of(DEMANDEE, PLANIFIEE, EN_COURS);
}