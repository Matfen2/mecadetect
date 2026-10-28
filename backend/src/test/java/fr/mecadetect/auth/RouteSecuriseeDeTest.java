package fr.mecadetect.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route présente uniquement dans les tests, pour vérifier la sécurité par rôle
 * avant que les vrais endpoints réservés à l'admin n'existent.
 */
@RestController
class RouteSecuriseeDeTest {

    @GetMapping("/api/v1/test-securite/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String routeAdmin() {
        return "ok";
    }
}