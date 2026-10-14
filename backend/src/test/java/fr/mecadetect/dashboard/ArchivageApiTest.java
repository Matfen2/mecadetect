package fr.mecadetect.dashboard;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tâche 4.6 : POST /api/v1/admin/archivage (ADMIN), et les deux autres endpoints du tag Dashboard
 * qui répondent 501 jusqu'au Sprint 5.
 * Dans la transaction du test, l'archivage ne valide pas ses lots : tout est annulé à la fin.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ArchivageApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private MockMvc mockMvc;
    private Utilisateur admin;
    private Utilisateur demandeur;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("AA", "Zone d'archivage API"));
        var presses = typeRepository.saveAndFlush(new TypeEquipement("Presse archivage API"));
        var presse = equipementRepository.saveAndFlush(
                new Equipement("ARC-API1", "Presse de 2014", LocalDate.of(2010, 1, 4), presses, zone));
        admin = utilisateurRepository.saveAndFlush(
                new Utilisateur("Martin", "Claire", "admin.arc@mecadetect.fr", "hash", Role.ADMIN));
        demandeur = utilisateurRepository.saveAndFlush(
                new Utilisateur("Moreau", "Sophie", "demandeur.arcapi@mecadetect.fr", "hash", Role.DEMANDEUR));

        // Une intervention annulée en 2014 : la seule archivable avant le 01/06/2014
        jdbc.update("""
                INSERT INTO intervention (numero, titre, description, priorite, statut, date_demande, motif_annulation,
                                          id_equipement, id_demandeur, id_modifie_par)
                VALUES ('INT-2014-900001', 'Bruit', 'Bruit anormal', 'BASSE', 'ANNULEE', ?, 'Doublon', ?, ?, ?)
                """, Timestamp.valueOf(LocalDateTime.of(2014, 3, 3, 9, 0)),
                presse.getId(), demandeur.getId(), demandeur.getId());
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions archiver(String corps, Utilisateur utilisateur) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/archivage")
                .with(connecte(utilisateur))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    @Test
    @DisplayName("L'admin archive : 200 avec le nombre d'interventions archivées")
    void archivage() throws Exception {
        archiver("{\"dateLimite\": \"2014-06-01\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignesArchivees").value(1));
    }

    @Test
    @DisplayName("L'archivage est réservé à l'admin : 403")
    void reserveAlAdmin() throws Exception {
        archiver("{\"dateLimite\": \"2014-06-01\"}", demandeur)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Une date limite de moins d'un an est refusée : la dernière année reste consultable (400)")
    void dateTropRecente() throws Exception {
        archiver("{\"dateLimite\": \"" + LocalDate.now().minusMonths(6) + "\"}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_ARCHIVAGE_INVALIDE"));
    }

    @Test
    @DisplayName("La date limite est obligatoire (400)")
    void dateObligatoire() throws Exception {
        archiver("{}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DONNEES_INVALIDES"));
    }

    @Test
    @DisplayName("Le tableau de bord n'est pas encore disponible : 501")
    void dashboardNonDisponible() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").with(connecte(admin)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NON_DISPONIBLE"));
    }

    @Test
    @DisplayName("L'export CSV n'est pas encore disponible : 501")
    void exportNonDisponible() throws Exception {
        mockMvc.perform(get("/api/v1/exports/interventions?depuis=2026-01-01&jusqua=2026-01-31").with(connecte(admin)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NON_DISPONIBLE"));
    }
}