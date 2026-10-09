package fr.mecadetect.erreur;

import fr.mecadetect.auth.AuthentificationEchoueeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * Transforme les exceptions en réponses au format Problem Details (RFC 9457),
 * toujours avec un code métier exploitable par le front.
 *
 * Volontairement, aucun gestionnaire générique (Exception.class) : les refus d'accès
 * de Spring Security (403) doivent continuer à être traités par Spring Security.
 */
@RestControllerAdvice
public class GestionErreursApi {

    private static final Logger LOG = LoggerFactory.getLogger(GestionErreursApi.class);

    // --------------------------------------------------------------- 400

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail donneesInvalides(MethodArgumentNotValidException exception) {
        List<Map<String, String>> erreurs = exception.getBindingResult().getFieldErrors().stream()
                .map(erreur -> Map.of(
                        "champ", erreur.getField(),
                        "message", String.valueOf(erreur.getDefaultMessage())))
                .toList();

        var probleme = probleme(HttpStatus.BAD_REQUEST, "Données invalides",
                "Certains champs sont invalides.", "DONNEES_INVALIDES");
        probleme.setProperty("erreurs", erreurs);
        return probleme;
    }

    @ExceptionHandler(DonneesInvalidesException.class)
    ProblemDetail donneesIncoherentes(DonneesInvalidesException exception) {
        return probleme(HttpStatus.BAD_REQUEST, "Données invalides", exception.getMessage(), exception.getCode());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail requeteIllisible(HttpMessageNotReadableException exception) {
        return probleme(HttpStatus.BAD_REQUEST, "Requête illisible",
                "Le corps de la requête est mal formé ou contient une valeur inattendue.", "REQUETE_ILLISIBLE");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreInvalide(MethodArgumentTypeMismatchException exception) {
        return probleme(HttpStatus.BAD_REQUEST, "Paramètre invalide",
                "La valeur du paramètre « " + exception.getName() + " » est invalide.", "PARAMETRE_INVALIDE");
    }

    // --------------------------------------------------------------- 401

    @ExceptionHandler(AuthentificationEchoueeException.class)
    ProblemDetail authentificationEchouee(AuthentificationEchoueeException exception) {
        return probleme(HttpStatus.UNAUTHORIZED, "Authentification échouée",
                exception.getMessage(), "AUTHENTIFICATION_ECHOUEE");
    }

    // --------------------------------------------------------------- 404

    @ExceptionHandler(RessourceIntrouvableException.class)
    ProblemDetail ressourceIntrouvable(RessourceIntrouvableException exception) {
        return probleme(HttpStatus.NOT_FOUND, "Ressource introuvable", exception.getMessage(), exception.getCode());
    }

    // --------------------------------------------------------------- 409

    @ExceptionHandler(RegleMetierException.class)
    ProblemDetail regleMetier(RegleMetierException exception) {
        return probleme(HttpStatus.CONFLICT, "Règle métier non respectée", exception.getMessage(), exception.getCode());
    }

    /** Verrouillage optimiste (@Version) : quelqu'un a modifié la donnée entre-temps (UC-DB6). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflitDeVersion(OptimisticLockingFailureException exception) {
        return probleme(HttpStatus.CONFLICT, "Modification concurrente",
                "Ces données ont été modifiées par un autre utilisateur. Rechargez la page.", "CONFLIT_VERSION");
    }

    /** Filet de sécurité : une contrainte de la base a refusé l'opération (unicité, clé étrangère...). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail contrainteBase(DataIntegrityViolationException exception) {
        LOG.warn("Contrainte d'intégrité violée : {}", exception.getMostSpecificCause().getMessage());
        return probleme(HttpStatus.CONFLICT, "Conflit de données",
                "L'opération viole une contrainte d'intégrité des données.", "CONFLIT_DONNEES");
    }

    // --------------------------------------------------------------- 501

    @ExceptionHandler(FonctionnaliteNonDisponibleException.class)
    ProblemDetail nonDisponible(FonctionnaliteNonDisponibleException exception) {
        return probleme(HttpStatus.NOT_IMPLEMENTED, "Fonctionnalité non disponible",
                exception.getMessage(), "NON_DISPONIBLE");
    }

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        var probleme = ProblemDetail.forStatusAndDetail(statut, detail);
        probleme.setTitle(titre);
        probleme.setProperty("code", code);
        return probleme;
    }
}