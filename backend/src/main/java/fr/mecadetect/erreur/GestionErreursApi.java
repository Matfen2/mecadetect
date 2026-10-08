package fr.mecadetect.erreur;

import fr.mecadetect.auth.AuthentificationEchoueeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Transforme les exceptions en réponses au format Problem Details (RFC 9457),
 * avec un code métier exploitable par le front.
 */
@RestControllerAdvice
public class GestionErreursApi {

    @ExceptionHandler(AuthentificationEchoueeException.class)
    ProblemDetail authentificationEchouee(AuthentificationEchoueeException exception) {
        var probleme = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
        probleme.setTitle("Authentification échouée");
        probleme.setProperty("code", "AUTHENTIFICATION_ECHOUEE");
        return probleme;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail donneesInvalides(MethodArgumentNotValidException exception) {
        List<Map<String, String>> erreurs = exception.getBindingResult().getFieldErrors().stream()
                .map(erreur -> Map.of(
                        "champ", erreur.getField(),
                        "message", String.valueOf(erreur.getDefaultMessage())))
                .toList();

        var probleme = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Certains champs sont invalides.");
        probleme.setTitle("Données invalides");
        probleme.setProperty("code", "DONNEES_INVALIDES");
        probleme.setProperty("erreurs", erreurs);
        return probleme;
    }
}
