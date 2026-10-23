package fr.mecadetect.intervention.domain;

/// Port vers les habilitations des techniciens (RG-09).
public interface Habilitations {

    /// @param idType       identifiant du type d'équipement
    /// @param idTechnicien identifiant du technicien
    /// @return vrai si le technicien est habilité sur ce type d'équipement
    boolean estHabilite(Long idType, Long idTechnicien);
}
