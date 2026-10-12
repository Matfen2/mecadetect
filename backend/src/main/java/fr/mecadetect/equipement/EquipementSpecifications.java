package fr.mecadetect.equipement;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Construit la clause WHERE de la recherche à partir des seuls filtres renseignés.
 * Chaque filtre est une Specification indépendante, combinée avec un ET logique.
 */
public final class EquipementSpecifications {

    private static final char CARACTERE_ECHAPPEMENT = '\\';

    private EquipementSpecifications() {
    }

    public static Specification<Equipement> selon(CriteresRechercheEquipement criteres) {
        List<Specification<Equipement>> filtres = new ArrayList<>();

        filtres.add(criteres.statut() != null ? statutEgal(criteres.statut()) : nonArchive());
        if (criteres.idType() != null) {
            filtres.add(typeEgal(criteres.idType()));
        }
        if (criteres.idZone() != null) {
            filtres.add(zoneEgale(criteres.idZone()));
        }
        if (criteres.texte() != null && !criteres.texte().isBlank()) {
            filtres.add(texteContenu(criteres.texte()));
        }
        return Specification.allOf(filtres);
    }

    static Specification<Equipement> statutEgal(StatutEquipement statut) {
        return (racine, requete, cb) -> cb.equal(racine.get("statut"), statut);
    }

    static Specification<Equipement> nonArchive() {
        return (racine, requete, cb) -> cb.notEqual(racine.get("statut"), StatutEquipement.ARCHIVE);
    }

    /** type.id : Hibernate utilise directement la clé étrangère id_type, sans jointure. */
    static Specification<Equipement> typeEgal(Long idType) {
        return (racine, requete, cb) -> cb.equal(racine.get("type").get("id"), idType);
    }

    static Specification<Equipement> zoneEgale(Long idZone) {
        return (racine, requete, cb) -> cb.equal(racine.get("zone").get("id"), idZone);
    }

    /** Recherche sur la référence ou le nom, sans tenir compte de la casse. */
    static Specification<Equipement> texteContenu(String texte) {
        String motif = "%" + echapper(texte.trim().toLowerCase(Locale.ROOT)) + "%";
        return (racine, requete, cb) -> cb.or(
                cb.like(cb.lower(racine.get("reference")), motif, CARACTERE_ECHAPPEMENT),
                cb.like(cb.lower(racine.get("nom")), motif, CARACTERE_ECHAPPEMENT));
    }

    /**
     * Les caractères % et _ sont des jokers en SQL : on les échappe pour qu'une
     * recherche « 50% » cherche bien le texte « 50% » et non « 50 suivi de n'importe quoi ».
     */
    static String echapper(String texte) {
        return texte
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}