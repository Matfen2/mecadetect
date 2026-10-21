package fr.mecadetect.technicien;

import fr.mecadetect.intervention.InterventionResume;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.utilisateur.Utilisateur;

import java.util.List;

/// Un technicien vu par l'admin qui planifie : ses habilitations et sa charge.
///
/// @param technicien            le technicien
/// @param habilitations         types d'équipement sur lesquels il est habilité (RG-09)
/// @param interventionsOuvertes nombre d'interventions ouvertes qui lui sont affectées
/// @param interventionEnCours   son intervention en cours, ou null s'il n'en a pas (RG-04)
public record TechnicienVue(
        Utilisateur technicien,
        List<TypeEquipement> habilitations,
        int interventionsOuvertes,
        InterventionResume interventionEnCours) {
}