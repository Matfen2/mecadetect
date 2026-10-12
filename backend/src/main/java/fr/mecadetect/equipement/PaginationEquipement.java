package fr.mecadetect.equipement;

import fr.mecadetect.erreur.DonneesInvalidesException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Transforme les paramètres page, size et sort de l'API en Pageable Spring Data.
 * Le tri n'accepte que des champs listés : on n'expose jamais directement
 * les noms internes des entités, et on ne trie pas sur un champ non indexable.
 */
public final class PaginationEquipement {

    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAX = 100;

    /** Nom public du champ de tri vers le chemin JPA correspondant. */
    private static final Map<String, String> CHAMPS_TRIABLES = Map.of(
            "reference", "reference",
            "nom", "nom",
            "dateMiseService", "dateMiseService",
            "statut", "statut",
            "zone", "zone.code",
            "type", "type.libelle");

    private PaginationEquipement() {
    }

    public static Pageable versPageable(Integer page, Integer taille, String tri) {
        int numero = page != null ? page : 0;
        int nombre = taille != null ? taille : TAILLE_PAR_DEFAUT;

        if (numero < 0 || nombre < 1 || nombre > TAILLE_MAX) {
            throw new DonneesInvalidesException("PAGINATION_INVALIDE",
                    "La page doit être positive et la taille comprise entre 1 et " + TAILLE_MAX + ".");
        }
        // L'identifiant en second critère garantit un ordre stable d'une page à l'autre
        return PageRequest.of(numero, nombre, versSort(tri).and(Sort.by("id")));
    }

    static Sort versSort(String tri) {
        if (tri == null || tri.isBlank()) {
            return Sort.by("reference");
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