package fr.mecadetect.intervention.domain;

import java.util.List;

/// Page demandée par un cas d'usage : numéro, taille et ordre de tri.
///
/// Type du domaine : la couche application ne dépend pas de la pagination de Spring Data.
/// L'adaptateur de persistance la convertit en `Pageable`.
///
/// @param numero numéro de page, à partir de 0
/// @param taille nombre d'éléments par page
/// @param tri    critères de tri, du plus important au moins important ; vide si l'ordre
///               est fixé par la requête elle-même
public record DemandePage(int numero, int taille, List<Tri> tri) {

    /// Copie défensive : la liste de tri ne peut plus être modifiée.
    ///
    /// @param numero numéro de page, à partir de 0
    /// @param taille nombre d'éléments par page
    /// @param tri    critères de tri
    public DemandePage {
        tri = List.copyOf(tri);
    }

    /// Page sans tri : l'ordre est fixé par la requête elle-même.
    ///
    /// @param numero numéro de page, à partir de 0
    /// @param taille nombre d'éléments par page
    /// @return la demande de page
    public static DemandePage sansTri(int numero, int taille) {
        return new DemandePage(numero, taille, List.of());
    }

    /// Un critère de tri.
    ///
    /// @param champ     propriété triée, par exemple `dateDemande`
    /// @param croissant vrai pour l'ordre croissant, faux pour l'ordre décroissant
    public record Tri(String champ, boolean croissant) {
    }
}
