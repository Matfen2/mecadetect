package fr.mecadetect.auth;

import com.jayway.jsonpath.JsonPath;
import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de l'API de bout en bout : filtres de sécurité, contrôleur, service et base Oracle.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthApiTest {

    private static final String MOT_DE_PASSE = "MotDePasse123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UtilisateurRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        creerUtilisateur("admin.test@mecadetect.fr", Role.ADMIN);
        creerUtilisateur("demandeur.test@mecadetect.fr", Role.DEMANDEUR);
    }

    private void creerUtilisateur(String email, Role role) {
        repository.saveAndFlush(new Utilisateur("Test", role.name(), email, passwordEncoder.encode(MOT_DE_PASSE), role));
    }

    private String corpsLogin(String email, String motDePasse) {
        return """
                {"email": "%s", "motDePasse": "%s"}
                """.formatted(email, motDePasse);
    }

    private String obtenirJeton(String email) throws Exception {
        var reponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLogin(email, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.token");
    }

    // ---------------------------------------------------------------
    // Tâche 1.2 : login
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Login valide : 200 avec un jeton et le résumé de l'utilisateur")
    void loginValide() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLogin("admin.test@mecadetect.fr", MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expireDans").value(3600))
                .andExpect(jsonPath("$.utilisateur.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Mot de passe incorrect : 401 au format Problem Details")
    void motDePasseIncorrect() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLogin("admin.test@mecadetect.fr", "MauvaisMotDePasse")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTIFICATION_ECHOUEE"));
    }

    @Test
    @DisplayName("Email mal formé : 400 avec le détail du champ en erreur")
    void emailInvalide() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsLogin("pas-un-email", MOT_DE_PASSE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DONNEES_INVALIDES"))
                .andExpect(jsonPath("$.erreurs[0].champ").value("email"));
    }

    // ---------------------------------------------------------------
    // Tâche 1.3 : protection des routes
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Sans jeton, une route protégée renvoie 401")
    void sansJeton() throws Exception {
        mockMvc.perform(get("/api/v1/test-securite/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Avec un jeton invalide, une route protégée renvoie 401")
    void jetonInvalide() throws Exception {
        mockMvc.perform(get("/api/v1/test-securite/admin")
                        .header("Authorization", "Bearer jeton.completement.faux"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un demandeur reçoit 403 sur une route réservée à l'admin")
    void demandeurInterditSurRouteAdmin() throws Exception {
        var jeton = obtenirJeton("demandeur.test@mecadetect.fr");

        mockMvc.perform(get("/api/v1/test-securite/admin")
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un admin accède à une route réservée à l'admin")
    void adminAutoriseSurRouteAdmin() throws Exception {
        var jeton = obtenirJeton("admin.test@mecadetect.fr");

        mockMvc.perform(get("/api/v1/test-securite/admin")
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk());
    }
}