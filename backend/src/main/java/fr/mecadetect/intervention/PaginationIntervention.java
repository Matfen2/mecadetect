package fr.mecadetect.intervention;

import fr.mecadetect.erreur.DonneesInvalidesException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/// Transforme page, size et sort en Pageable, avec une liste blanche de champs triables.
/// Par défaut, les demandes les plus récentes d'abord.
public final class PaginationIntervention {

    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAX = 100;

    /// Tri par défaut : la date de demande, la plus récente d'abord.
    private static final String DATE_DEMANDE = "dateDemande";

    private static final Map<String, String> CHAMPS_TRIABLES = Map.of(
            "numero", "numero",
            DATE_DEMANDE, DATE_DEMANDE,
            "datePlanifiee", "datePlanifiee",
            "statut", "statut");

    private PaginationIntervention() {
    }

    /// Pagination triée selon le paramètre sort (dateDemande décroissante par défaut).
    ///
    /// L'identifiant départage les ex aequo, dans le même sens que le tri demandé : un index
    /// (date_demande, id_intervention) lu à l'envers fournit alors les lignes déjà triées,
    /// et Oracle s'arrête après la page demandée au lieu de trier toute la table (tâche 4.4).
    ///
    /// @param page   numéro de page, à partir de 0 ; null pour la première
    /// @param taille nombre d'éléments par page, de 1 à 100 ; null pour 20
    /// @param tri    champ et sens, par exemple `numero,desc` ; null pour le tri par défaut
    /// @return la pagination correspondante
    /// @throws DonneesInvalidesException si la page, la taille ou le tri est invalide
    public static Pageable versPageable(Integer page, Integer taille, String tri) {
        Sort sort = versSort(tri);
        Sort.Direction sens = sort.iterator().next().getDirection();
        return PageRequest.of(numero(page), nombre(taille), sort.and(Sort.by(sens, "id")));
    }

    /// Pagination sans tri : l'ordre est fixé par la requête elle-même.
    ///
    /// @param page   numéro de page, à partir de 0 ; null pour la première
    /// @param taille nombre d'éléments par page, de 1 à 100 ; null pour 20
    /// @return la pagination correspondante
    /// @throws DonneesInvalidesException si la page ou la taille est invalide
    public static Pageable sansTri(Integer page, Integer taille) {
        return PageRequest.of(numero(page), nombre(taille));
    }

    private static int numero(Integer page) {
        int numero = page != null ? page : 0;
        if (numero < 0) {
            throw new DonneesInvalidesException("PAGINATION_INVALIDE", "Le numéro de page doit être positif.");
        }
        return numero;
    }

    private static int nombre(Integer taille) {
        int nombre = taille != null ? taille : TAILLE_PAR_DEFAUT;
        if (nombre < 1 || nombre > TAILLE_MAX) {
            throw new DonneesInvalidesException("PAGINATION_INVALIDE",
                    "La taille de page doit être comprise entre 1 et " + TAILLE_MAX + ".");
        }
        return nombre;
    }

    static Sort versSort(String tri) {
        if (tri == null || tri.isBlank()) {
            return Sort.by(Sort.Direction.DESC, DATE_DEMANDE);
        }
        String[] morceaux = tri.split(",");
        String chemin = CHAMPS_TRIABLES.get(morceaux[0].trim());
        if (chemin == null) {
            throw new DonneesInvalidesException("TRI_INVALIDE",
                    "Tri impossible sur « " + morceaux[0].trim() + " ». Champs autorisés : " + CHAMPS_TRIABLES.keySet());
        }
        Sort.Direction direction = morceaux.length > 1
                ? Sort.Direction.fromOptionalString(morceaux[1].trim())
                    .orElseThrow(() -> new DonneesInvalidesException("TRI_INVALIDE",
                            "Le sens de tri doit être asc ou desc."))
                : Sort.Direction.ASC;
        return Sort.by(direction, chemin);
    }
}