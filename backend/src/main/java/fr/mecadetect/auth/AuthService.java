package fr.mecadetect.auth;

import fr.mecadetect.securite.JwtService;
import fr.mecadetect.securite.JwtService.JetonGenere;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Authentification par email et mot de passe, puis émission d'un jeton JWT.
@Service
public class AuthService {

    private final UtilisateurRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /// Empreinte factice comparée quand l'email est inconnu : la réponse prend alors
    /// le même temps qu'avec un vrai compte, ce qui empêche de deviner quels emails
    /// existent en mesurant le temps de réponse (attaque temporelle).
    private final String empreinteFactice;

    public AuthService(UtilisateurRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.empreinteFactice = passwordEncoder.encode("mot-de-passe-factice");
    }

    /// Vérifie les identifiants et génère un jeton pour le compte.
    ///
    /// Un email inconnu, un mot de passe incorrect et un compte désactivé donnent la même erreur,
    /// dans le même temps de réponse : impossible de deviner quels comptes existent.
    ///
    /// @param email      email saisi, sans tenir compte de la casse
    /// @param motDePasse mot de passe saisi
    /// @return le jeton généré et l'utilisateur connecté
    /// @throws AuthentificationEchoueeException si la connexion est refusée
    @Transactional(readOnly = true)
    public ResultatConnexion connecter(String email, String motDePasse) {
        var utilisateur = repository.findByEmailIgnoreCase(email);

        String empreinte = utilisateur.map(Utilisateur::getMotDePasseHash).orElse(empreinteFactice);
        boolean motDePasseCorrect = passwordEncoder.matches(motDePasse, empreinte);

        if (utilisateur.isEmpty() || !motDePasseCorrect || !utilisateur.get().isActif()) {
            throw new AuthentificationEchoueeException();
        }

        return new ResultatConnexion(jwtService.generer(utilisateur.get()), utilisateur.get());
    }

    /// Résultat d'une connexion réussie.
    ///
    /// @param jeton       jeton JWT et sa durée de validité
    /// @param utilisateur utilisateur connecté
    public record ResultatConnexion(JetonGenere jeton, Utilisateur utilisateur) {
    }
}