package fr.mecadetect.technicien;

import fr.mecadetect.intervention.InterventionResume;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.utilisateur.Utilisateur;

import java.util.List;

/** Un technicien vu par l'admin qui planifie : ses habilitations et sa charge. */
public record TechnicienVue(
        Utilisateur technicien,
        List<TypeEquipement> habilitations,
        int interventionsOuvertes,
        InterventionResume interventionEnCours) {
}