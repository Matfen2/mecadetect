package fr.mecadetect.auth;

import fr.mecadetect.api.AuthApi;
import fr.mecadetect.api.dto.LoginRequestDto;
import fr.mecadetect.api.dto.LoginResponseDto;
import fr.mecadetect.api.dto.RoleDto;
import fr.mecadetect.api.dto.UtilisateurResumeDto;
import fr.mecadetect.utilisateur.Utilisateur;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/// Implémente l'interface `AuthApi` générée depuis `openapi.yaml` :
/// la route, la méthode HTTP et la validation viennent du contrat.
@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /// Authentifie l'utilisateur et lui renvoie un jeton JWT.
    ///
    /// @param loginRequestDto email et mot de passe saisis
    /// @return le jeton, sa durée de validité et le résumé de l'utilisateur connecté
    @Override
    public ResponseEntity<LoginResponseDto> login(LoginRequestDto loginRequestDto) {
        AuthService.ResultatConnexion connexion =
                authService.connecter(loginRequestDto.getEmail(), loginRequestDto.getMotDePasse());
        Utilisateur utilisateurConnecte = connexion.utilisateur();

        var resume = new UtilisateurResumeDto()
                .idUtilisateur(utilisateurConnecte.getId())
                .nom(utilisateurConnecte.getNom())
                .prenom(utilisateurConnecte.getPrenom())
                .role(RoleDto.fromValue(utilisateurConnecte.getRole().name()));

        var reponse = new LoginResponseDto()
                .token(connexion.jeton().valeur())
                .expireDans((int) connexion.jeton().expireDansSecondes())
                .utilisateur(resume);

        return ResponseEntity.ok(reponse);
    }
}