package fr.mecadetect.securite;

import fr.mecadetect.utilisateur.Utilisateur;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Génère les jetons JWT. Le jeton contient l'identifiant de l'utilisateur (sub),
 * son email et son rôle, avec une durée de validité courte.
 */
@Service
public class JwtService {

    private static final String EMETTEUR = "mecadetect";

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration duree;

    public JwtService(JwtEncoder encoder,
                      Clock clock,
                      @Value("${mecadetect.securite.jwt.duree:PT1H}") Duration duree) {
        this.encoder = encoder;
        this.clock = clock;
        this.duree = duree;
    }

    public JetonGenere generer(Utilisateur utilisateur) {
        Instant maintenant = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMETTEUR)
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(duree))
                .subject(String.valueOf(utilisateur.getId()))
                .claim("email", utilisateur.getEmail())
                .claim("role", utilisateur.getRole().name())
                .build();

        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        String valeur = encoder.encode(JwtEncoderParameters.from(entete, claims)).getTokenValue();

        return new JetonGenere(valeur, duree.toSeconds());
    }

    public record JetonGenere(String valeur, long expireDansSecondes) {
    }
}