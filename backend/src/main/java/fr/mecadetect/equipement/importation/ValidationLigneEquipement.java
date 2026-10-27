package fr.mecadetect.equipement.importation;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.Zone;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// Valide une ligne du fichier d'import et la transforme en équipement prêt à enregistrer.
///
/// Classe Java simple, sans Spring Batch : elle se teste sans base. Le job l'utilise comme
/// processeur ; une ligne invalide lève une [LigneRejeteeException], que le step ignore (skip).
///
/// Une instance par exécution du job : elle mémorise les références déjà vues dans le fichier.
public class ValidationLigneEquipement {

    private static final int LONGUEUR_MAX_REFERENCE = 30;
    private static final int LONGUEUR_MAX_NOM = 100;

    /// Formats acceptés : ISO, et celui d'Excel en français. `uuuu` et le mode strict refusent
    /// les dates impossibles (30 février) au lieu de les corriger.
    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));

    private final Map<String, TypeEquipement> typesParLibelle;
    private final Map<String, Zone> zonesParCode;
    private final Predicate<String> referenceDejaEnBase;
    private final LocalDate aujourdHui;

    /// Référence en minuscules, et numéro de la première ligne du fichier qui l'utilise.
    private final Map<String, Integer> referencesDuFichier = new HashMap<>();

    /// @param types               types d'équipement du référentiel
    /// @param zones               zones du référentiel
    /// @param referenceDejaEnBase vrai si un équipement porte déjà cette référence, sans tenir compte de la casse
    /// @param aujourdHui          date du jour : une mise en service ne peut pas être postérieure
    public ValidationLigneEquipement(Collection<TypeEquipement> types, Collection<Zone> zones,
                                     Predicate<String> referenceDejaEnBase, LocalDate aujourdHui) {
        this.typesParLibelle = indexer(types, TypeEquipement::getLibelle);
        this.zonesParCode = indexer(zones, Zone::getCode);
        this.referenceDejaEnBase = referenceDejaEnBase;
        this.aujourdHui = aujourdHui;
    }

    /// Valide la ligne et crée l'équipement correspondant, en service.
    ///
    /// @param ligne ligne lue dans le fichier
    /// @return l'équipement à enregistrer
    /// @throws LigneRejeteeException si la ligne est invalide, avec le motif du rejet
    public Equipement valider(LigneEquipementCsv ligne) {
        int numero = ligne.numeroLigne();
        String reference = obligatoire(ligne.reference(), numero, "La référence", LONGUEUR_MAX_REFERENCE);
        String nom = obligatoire(ligne.nom(), numero, "Le nom", LONGUEUR_MAX_NOM);
        LocalDate dateMiseService = date(ligne.dateMiseService(), numero);
        TypeEquipement type = trouver(typesParLibelle, ligne.type(), numero, "Le type d'équipement");
        Zone zone = trouver(zonesParCode, ligne.zone(), numero, "La zone");
        verifierReferenceUnique(reference, numero);

        return new Equipement(reference, nom, dateMiseService, type, zone);
    }

    private static String obligatoire(String valeur, int numero, String champ, int longueurMax) {
        String nettoyee = valeur == null ? "" : valeur.strip();
        if (nettoyee.isEmpty()) {
            throw new LigneRejeteeException(numero, champ + " est obligatoire.");
        }
        if (nettoyee.length() > longueurMax) {
            throw new LigneRejeteeException(numero, champ + " dépasse " + longueurMax + " caractères.");
        }
        return nettoyee;
    }

    private LocalDate date(String valeur, int numero) {
        String texte = valeur == null ? "" : valeur.strip();
        LocalDate date = FORMATS_DATE.stream()
                .map(format -> lire(texte, format))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new LigneRejeteeException(numero, "La date de mise en service « " + texte
                        + " » est invalide (formats acceptés : AAAA-MM-JJ ou JJ/MM/AAAA)."));
        if (date.isAfter(aujourdHui)) {
            throw new LigneRejeteeException(numero, "La date de mise en service ne peut pas être dans le futur.");
        }
        return date;
    }

    private static LocalDate lire(String texte, DateTimeFormatter format) {
        try {
            return LocalDate.parse(texte, format);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static <T> T trouver(Map<String, T> index, String valeur, int numero, String element) {
        String texte = valeur == null ? "" : valeur.strip();
        T trouve = index.get(cle(texte));
        if (trouve == null) {
            throw new LigneRejeteeException(numero, element + " « " + texte + " » n'existe pas.");
        }
        return trouve;
    }

    /// Une référence ne doit exister ni en base, ni plus haut dans le fichier.
    ///
    /// Une même ligne peut être validée deux fois : quand l'écriture d'un lot échoue, Spring Batch
    /// relit le lot ligne par ligne pour isoler la fautive. Ce n'est pas un doublon.
    private void verifierReferenceUnique(String reference, int numero) {
        if (referenceDejaEnBase.test(reference)) {
            throw new LigneRejeteeException(numero,
                    "La référence « " + reference + " » est déjà utilisée par un équipement existant.");
        }
        Integer premiereLigne = referencesDuFichier.putIfAbsent(cle(reference), numero);
        if (premiereLigne != null && premiereLigne != numero) {
            throw new LigneRejeteeException(numero,
                    "La référence « " + reference + " » apparaît déjà à la ligne " + premiereLigne + " du fichier.");
        }
    }

    private static <T> Map<String, T> indexer(Collection<T> elements, Function<T, String> libelle) {
        return elements.stream().collect(Collectors.toMap(
                element -> cle(libelle.apply(element)), Function.identity(), (premier, second) -> premier));
    }

    /// Comparaison sans tenir compte de la casse ni des espaces autour.
    private static String cle(String texte) {
        return texte.strip().toLowerCase(Locale.ROOT);
    }
}
