package fr.mecadetect.securite;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.util.Base64;

/**
 * Sécurité de l'API :
 * - sans état (pas de session, pas de cookie, donc pas de CSRF) ;
 * - authentification par JWT signé en HS256 avec une clé secrète ;
 * - le rôle est porté par la claim "role" et converti en ROLE_ADMIN, ROLE_TECHNICIEN... ;
 * - les règles fines par rôle sont posées avec @PreAuthorize sur les méthodes.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] ROUTES_PUBLIQUES = {
            "/actuator/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/openapi.yaml"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(ROUTES_PUBLIQUES).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** Convertit la claim "role" du jeton en autorité Spring Security (ROLE_xxx). */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var autorites = new JwtGrantedAuthoritiesConverter();
        autorites.setAuthoritiesClaimName("role");
        autorites.setAuthorityPrefix("ROLE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(autorites);
        return converter;
    }

    @Bean
    SecretKey cleJwt(@Value("${mecadetect.securite.jwt.secret}") String secretBase64) {
        byte[] octets = Base64.getDecoder().decode(secretBase64);
        if (octets.length < 32) {
            throw new IllegalStateException("La clé JWT doit faire au moins 256 bits (32 octets) pour HS256");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey cleJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(cleJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey cleJwt) {
        return NimbusJwtDecoder.withSecretKey(cleJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}