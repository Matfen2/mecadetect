package fr.mecadetect.auth;

import fr.mecadetect.api.AuthApi;
import fr.mecadetect.api.dto.LoginRequestDto;
import fr.mecadetect.api.dto.LoginResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/// Implémente l'interface `AuthApi` générée depuis `openapi.yaml` :
/// la route, la méthode HTTP et la validation viennent du contrat.
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final AuthMapper mapper;

    /// Authentifie l'utilisateur et lui renvoie un jeton JWT.
    ///
    /// @param loginRequestDto email et mot de passe saisis
    /// @return le jeton, sa durée de validité et le résumé de l'utilisateur connecté
    @Override
    public ResponseEntity<LoginResponseDto> login(LoginRequestDto loginRequestDto) {
        AuthService.ResultatConnexion connexion =
                authService.connecter(loginRequestDto.getEmail(), loginRequestDto.getMotDePasse());
        return ResponseEntity.ok(mapper.versDto(connexion));
    }
}
