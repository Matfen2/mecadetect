package fr.mecadetect.intervention;

/// Priorité d'une intervention : valeurs identiques à la contrainte `ck_intervention_priorite`.
///
/// Les valeurs sont déclarées **par ordre d'urgence décroissante** : [#values()] les renvoie
/// dans cet ordre, qui sert notamment à l'affichage du tableau de bord.
/// La base stocke le nom de la valeur, pas sa position : changer cet ordre est sans effet sur les données.
public enum Priorite {
    CRITIQUE,
    HAUTE,
    NORMALE,
    BASSE
}