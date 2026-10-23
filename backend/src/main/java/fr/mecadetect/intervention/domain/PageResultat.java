package fr.mecadetect.intervention.domain;

import java.util.List;

/// Page de résultats renvoyée par un port de persistance.
///
/// @param contenu       éléments de la page
/// @param numero        numéro de la page, à partir de 0
/// @param taille        taille de page demandée
/// @param totalElements nombre total d'éléments, toutes pages confondues
/// @param <T>           type des éléments
public record PageResultat<T>(List<T> contenu, int numero, int taille, long totalElements) {

    /// Copie défensive : le contenu ne peut plus être modifié.
    ///
    /// @param contenu       éléments de la page
    /// @param numero        numéro de la page
    /// @param taille        taille de page demandée
    /// @param totalElements nombre total d'éléments
    public PageResultat {
        contenu = List.copyOf(contenu);
    }

    /// Nombre de pages, calculé comme Spring Data : une seule page si la taille est nulle.
    ///
    /// @return le nombre total de pages
    public int totalPages() {
        return taille == 0 ? 1 : (int) Math.ceil((double) totalElements / taille);
    }
}
