package fr.mecadetect.export;

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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tâche 5.3 : GET /api/v1/exports/interventions, export CSV d'une période (ADMIN).
 * Les données de test datent de mars 2015 : aucune autre donnée de test à cette période.
 * Dates stockées en UTC, affichées et filtrées à l'heure de Paris (UTC+1 en mars).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ExportInterventionsApiTest {

    private static final String ENTETE = "Numéro;Titre;Référence équipement;Équipement;Priorité;Statut;Demandeur;"
            + "Technicien;Demandée le;Planifiée le;Débutée le;Terminée le;Temps passé (min);Compte rendu;"
            + "Motif d'annulation\r\n";

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;

    private MockMvc mockMvc;
    private Utilisateur admin;
    private Utilisateur demandeur;
    private Utilisateur technicien;
    private Equipement presse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var zone = zoneRepository.saveAndFlush(new Zone("EX", "Zone d'export"));
        var presses = typeRepository.saveAndFlush(new TypeEquipement("Presse d'export"));
        presse = equipementRepository.saveAndFlush(
                new Equipement("PRS-EXP", "Presse 225 t", LocalDate.of(2010, 1, 4), presses, zone));
        admin = utilisateur("Martin", "Claire", "admin.exp@mecadetect.fr", Role.ADMIN);
        demandeur = utilisateur("Moreau", "Sophie", "demandeur.exp@mecadetect.fr", Role.DEMANDEUR);
        technicien = utilisateur("Bernard", "Lucas", "technicien.exp@mecadetect.fr", Role.TECHNICIEN);

        // Dans la période, dans le désordre : l'export les trie par date de demande
        intervention("INT-EXP-000002", "Bruit; \"anormal\"", "NORMALE", LocalDateTime.of(2015, 3, 20, 13, 30));
        jdbc.update("""
                UPDATE intervention SET statut = 'ANNULEE', motif_annulation = 'Doublon' WHERE numero = 'INT-EXP-000002'
                """);
        intervention("INT-EXP-000001", "Fuite d'huile", "HAUTE", LocalDateTime.of(2015, 3, 2, 8, 0));
        jdbc.update("""
                UPDATE intervention
                   SET statut = 'TERMINEE', id_technicien = ?, date_planifiee = ?, date_debut = ?, date_fin = ?,
                       temps_passe_min = 90, compte_rendu = ?, id_modifie_par = ?
                 WHERE numero = 'INT-EXP-000001'
                """, technicien.getId(), Timestamp.valueOf(LocalDateTime.of(2015, 3, 2, 9, 0)),
                Timestamp.valueOf(LocalDateTime.of(2015, 3, 2, 10, 0)), Timestamp.valueOf(LocalDateTime.of(2015, 3, 2, 11, 30)),
                "Joint remplacé\nRéglage OK", technicien.getId());
        // Hors période : le 31 mars à 23 h 30 UTC, c'est déjà le 1er avril à Paris
        intervention("INT-EXP-000003", "Vibrations", "BASSE", LocalDateTime.of(2015, 3, 31, 23, 30));
    }

    private Utilisateur utilisateur(String nom, String prenom, String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur(nom, prenom, email, "hash", role));
    }

    private void intervention(String numero, String titre, String priorite, LocalDateTime demandeUtc) {
        jdbc.update("""
                INSERT INTO intervention (numero, titre, description, priorite, statut, date_demande,
                                          id_equipement, id_demandeur, id_modifie_par)
                VALUES (?, ?, 'Description', ?, 'DEMANDEE', ?, ?, ?, ?)
                """, numero, titre, priorite, Timestamp.valueOf(demandeUtc), presse.getId(), demandeur.getId(),
                demandeur.getId());
    }

    private static RequestPostProcessor connecte(Utilisateur utilisateur) {
        return jwt().jwt(jeton -> jeton.subject(String.valueOf(utilisateur.getId())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
    }

    private ResultActions exporter(String depuis, String jusqua, Utilisateur utilisateur) throws Exception {
        return mockMvc.perform(get("/api/v1/exports/interventions")
                .param("depuis", depuis).param("jusqua", jusqua)
                .with(connecte(utilisateur)));
    }

    private static String contenu(ResultActions resultat) {
        byte[] octets = resultat.andReturn().getResponse().getContentAsByteArray();
        return new String(octets, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Fichier CSV téléchargeable, UTF-8 avec BOM pour Excel, une ligne d'en-tête")
    void fichierCsv() throws Exception {
        var resultat = exporter("2015-03-01", "2015-03-31", admin)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"interventions-2015-03-01-au-2015-03-31.csv\""));

        String csv = contenu(resultat);
        assertThat(csv).startsWith("﻿" + ENTETE);
    }

    @Test
    @DisplayName("Les interventions de la période, triées par date de demande, avec libellés et heures de Paris")
    void lignes() throws Exception {
        String csv = contenu(exporter("2015-03-01", "2015-03-31", admin).andExpect(status().isOk()));

        String[] lignes = csv.substring(1 + ENTETE.length()).split("\r\n");
        assertThat(lignes).hasSize(2);
        assertThat(lignes[0]).isEqualTo("INT-EXP-000001;Fuite d'huile;PRS-EXP;Presse 225 t;Haute;Terminée;"
                + "Sophie Moreau;Lucas Bernard;02/03/2015 09:00;02/03/2015 10:00;02/03/2015 11:00;02/03/2015 12:30;"
                + "90;\"Joint remplacé\nRéglage OK\";");
        assertThat(lignes[1]).isEqualTo("INT-EXP-000002;\"Bruit; \"\"anormal\"\"\";PRS-EXP;Presse 225 t;Normale;"
                + "Annulée;Sophie Moreau;;20/03/2015 14:30;;;;;;Doublon");
    }

    @Test
    @DisplayName("Une période sans intervention donne un fichier avec la seule ligne d'en-tête")
    void periodeVide() throws Exception {
        assertThat(contenu(exporter("2014-01-01", "2014-01-31", admin).andExpect(status().isOk())))
                .isEqualTo("﻿" + ENTETE);
    }

    @Test
    @DisplayName("Une période inversée est refusée (400)")
    void periodeInversee() throws Exception {
        exporter("2015-03-31", "2015-03-01", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIODE_INVALIDE"));
    }

    @Test
    @DisplayName("Une période de plus d'un an est refusée (400)")
    void periodeTropLongue() throws Exception {
        exporter("2014-01-01", "2015-03-31", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIODE_TROP_LONGUE"));
    }

    @Test
    @DisplayName("L'export est réservé à l'admin : 403")
    void reserveAlAdmin() throws Exception {
        exporter("2015-03-01", "2015-03-31", demandeur)
                .andExpect(status().isForbidden());
    }
}