package fr.mecadetect.dashboard;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.YearMonth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tâche 5.1 : GET /api/v1/dashboard renvoie tous les indicateurs en un appel (ADMIN). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DashboardApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private Clock clock;

    private MockMvc mockMvc;
    private Utilisateur admin;
    private Utilisateur demandeur;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = utilisateurRepository.saveAndFlush(
                new Utilisateur("Martin", "Claire", "admin.dshapi@mecadetect.fr", "hash", Role.ADMIN));
        demandeur = utilisateurRepository.saveAndFlush(
                new Utilisateur("Moreau", "Sophie", "demandeur.dshapi@mecadetect.fr", "hash", Role.DEMANDEUR));
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    @Test
    @DisplayName("L'admin obtient tous les indicateurs en un seul appel")
    void indicateurs() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").with(connecte(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ouvertesParPriorite[*].priorite",
                        contains("CRITIQUE", "HAUTE", "NORMALE", "BASSE")))
                .andExpect(jsonPath("$.equipementsEnPanne").isNumber())
                .andExpect(jsonPath("$.tempsMoyenResolutionHeures").isNumber())
                .andExpect(jsonPath("$.evolutionMensuelle", hasSize(12)))
                .andExpect(jsonPath("$.evolutionMensuelle[11].mois").value(YearMonth.now(clock).toString()))
                .andExpect(jsonPath("$.topEquipements").isArray())
                .andExpect(jsonPath("$.chargeTechniciens").isArray());
    }

    @Test
    @DisplayName("Le tableau de bord est réservé à l'admin : 403")
    void reserveAlAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").with(connecte(demandeur)))
                .andExpect(status().isForbidden());
    }
}