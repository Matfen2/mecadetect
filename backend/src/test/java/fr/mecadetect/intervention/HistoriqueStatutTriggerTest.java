package fr.mecadetect.intervention;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.utilisateur.Habilitation;
import fr.mecadetect.utilisateur.HabilitationRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tâche 4.2 (UC-DB1, RG-08) : le trigger Oracle historise chaque changement de statut.
 * Chaque transition est suivie d'un flush, comme dans l'application où chaque action
 * est une requête HTTP distincte : le trigger voit ainsi chaque UPDATE séparément.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HistoriqueStatutTriggerTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Autowired private WebApplicationContext context;
    @Autowired private InterventionRepository interventionRepository;
    @Autowired private HistoriqueStatutRepository historiqueRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private Equipement presse;
    private Utilisateur admin;
    private Utilisateur demandeur;
    private Utilisateur technicien;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        var type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
        presse = equipementRepository.saveAndFlush(
                new Equipement("TST-001", "Presse de test", LocalDate.of(2020, 1, 6), type, zone));
        admin = utilisateur("Martin", "admin.histo@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("Moreau", "demandeur.histo@mecadetect.fr", Role.DEMANDEUR);
        technicien = utilisateur("Bernard", "technicien.histo@mecadetect.fr", Role.TECHNICIEN);
        habilitationRepository.saveAndFlush(new Habilitation(type, technicien, LocalDate.of(2025, 1, 6)));
    }

    private Utilisateur utilisateur(String nom, String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur(nom, "Test", email, "hash", role));
    }

    private Intervention declarer() {
        return interventionRepository.saveAndFlush(new Intervention(interventionRepository.prochainNumero(2026),
                "Fuite d'huile", "Fuite sur le vérin principal", Priorite.HAUTE, presse, demandeur, LUNDI_8H));
    }

    private java.util.List<HistoriqueStatut> historique(Intervention intervention) {
        return historiqueRepository.findByInterventionIdOrderByDateChangementAscIdAsc(intervention.getId());
    }

    private int nombreDeLignes(Intervention intervention) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM historique_statut WHERE id_intervention = ?",
                Integer.class, intervention.getId());
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    @Test
    @DisplayName("La création d'une intervention écrit « (aucun) → DEMANDEE », par le demandeur, à l'instant présent (UTC)")
    void creation() {
        var intervention = declarer();

        var lignes = historique(intervention);
        assertThat(lignes).singleElement().satisfies(ligne -> {
            assertThat(ligne.getAncienStatut()).isNull();
            assertThat(ligne.getNouveauStatut()).isEqualTo(StatutIntervention.DEMANDEE);
            assertThat(ligne.getAuteur().getId()).isEqualTo(demandeur.getId());
            assertThat(Duration.between(ligne.getDateChangement(), LocalDateTime.now(ZoneOffset.UTC)).abs())
                    .isLessThan(Duration.ofMinutes(1));
        });
    }

    @Test
    @DisplayName("Chaque transition du cycle complet ajoute une ligne avec son auteur")
    void cycleComplet() {
        var intervention = declarer();
        intervention.planifier(technicien, LUNDI_8H, admin);
        interventionRepository.saveAndFlush(intervention);
        intervention.demarrer(technicien, LUNDI_8H.plusMinutes(5));
        interventionRepository.saveAndFlush(intervention);
        intervention.cloturer("Joint du vérin remplacé", 45, technicien, LUNDI_8H.plusMinutes(50));
        interventionRepository.saveAndFlush(intervention);

        assertThat(historique(intervention))
                .extracting(HistoriqueStatut::getAncienStatut, HistoriqueStatut::getNouveauStatut, l -> l.getAuteur().getId())
                .containsExactly(
                        tuple(null, StatutIntervention.DEMANDEE, demandeur.getId()),
                        tuple(StatutIntervention.DEMANDEE, StatutIntervention.PLANIFIEE, admin.getId()),
                        tuple(StatutIntervention.PLANIFIEE, StatutIntervention.EN_COURS, technicien.getId()),
                        tuple(StatutIntervention.EN_COURS, StatutIntervention.TERMINEE, technicien.getId()));
    }

    @Test
    @DisplayName("L'annulation est historisée avec l'admin pour auteur")
    void annulation() {
        var intervention = declarer();
        intervention.annuler("Doublon de la demande précédente", admin);
        interventionRepository.saveAndFlush(intervention);

        assertThat(historique(intervention)).last().satisfies(ligne -> {
            assertThat(ligne.getAncienStatut()).isEqualTo(StatutIntervention.DEMANDEE);
            assertThat(ligne.getNouveauStatut()).isEqualTo(StatutIntervention.ANNULEE);
            assertThat(ligne.getAuteur().getId()).isEqualTo(admin.getId());
        });
    }

    @Test
    @DisplayName("Une mise à jour qui ne change pas le statut n'écrit rien")
    void sansChangementDeStatut() {
        var intervention = declarer();

        jdbc.update("UPDATE intervention SET titre = 'Fuite importante' WHERE id_intervention = ?", intervention.getId());
        jdbc.update("UPDATE intervention SET statut = statut WHERE id_intervention = ?", intervention.getId());

        assertThat(nombreDeLignes(intervention)).isEqualTo(1);
    }

    @Test
    @DisplayName("La garantie vient de la base : un UPDATE SQL direct est historisé aussi")
    void updateSqlDirect() {
        var intervention = declarer();

        jdbc.update("""
                UPDATE intervention
                   SET statut = 'ANNULEE', motif_annulation = 'Annulée par script', id_modifie_par = ?
                 WHERE id_intervention = ?
                """, admin.getId(), intervention.getId());

        assertThat(jdbc.queryForList("""
                SELECT ancien_statut, nouveau_statut, id_auteur FROM historique_statut
                 WHERE id_intervention = ? ORDER BY id_historique
                """, intervention.getId()))
                .hasSize(2)
                .last()
                .satisfies(ligne -> {
                    assertThat(ligne.get("ANCIEN_STATUT")).isEqualTo("DEMANDEE");
                    assertThat(ligne.get("NOUVEAU_STATUT")).isEqualTo("ANNULEE");
                    assertThat(((Number) ligne.get("ID_AUTEUR")).longValue()).isEqualTo(admin.getId());
                });
    }

    @Test
    @DisplayName("Le détail renvoyé par l'API après une planification contient l'historique")
    void historiqueDansLeDetail() throws Exception {
        var intervention = declarer();

        mockMvc.perform(post("/api/v1/interventions/{id}/planifier", intervention.getId())
                        .with(connecte(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "idTechnicien": %d, "datePlanifiee": "2026-01-06T08:00:00Z"}
                                """.formatted(technicien.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historique.length()").value(2))
                .andExpect(jsonPath("$.historique[1].ancienStatut").value("DEMANDEE"))
                .andExpect(jsonPath("$.historique[1].nouveauStatut").value("PLANIFIEE"))
                .andExpect(jsonPath("$.historique[1].auteur.nom").value("Martin"));

        mockMvc.perform(get("/api/v1/interventions/{id}", intervention.getId()).with(connecte(demandeur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historique.length()").value(2))
                .andExpect(jsonPath("$.historique[0].nouveauStatut").value("DEMANDEE"));
    }
}