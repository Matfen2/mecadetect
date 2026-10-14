package fr.mecadetect.intervention;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tâche 3.8 (UC-DB6) : deux admins planifient la même intervention en même temps.
 *
 * Le scénario est rejoué de façon déterministe avec deux transactions imbriquées :
 * l'admin A lit l'intervention (version 0), l'admin B la lit aussi, la planifie et valide ;
 * quand A valide à son tour, Oracle ne trouve plus de ligne en version 0 : sa mise à jour échoue.
 *
 * Ce test n'est pas @Transactional (il faut de vrais commits) : il nettoie donc ses données,
 * historique compris (écrit par le trigger V3).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrenceInterventionTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private InterventionRepository interventionRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private JdbcTemplate jdbc;

    private Zone zone;
    private TypeEquipement type;
    private Equipement equipement;
    private Utilisateur adminA;
    private Utilisateur adminB;
    private Utilisateur technicienA;
    private Utilisateur technicienB;
    private Utilisateur demandeur;
    private Intervention intervention;

    @BeforeEach
    void setUp() {
        zone = zoneRepository.save(new Zone("CC1", "Zone concurrence"));
        type = typeRepository.save(new TypeEquipement("Type concurrence"));
        equipement = equipementRepository.save(
                new Equipement("CONC-001", "Équipement concurrence", LocalDate.of(2020, 1, 6), type, zone));
        adminA = utilisateur("admin-a.conc@mecadetect.fr", Role.ADMIN);
        adminB = utilisateur("admin-b.conc@mecadetect.fr", Role.ADMIN);
        technicienA = utilisateur("technicien-a.conc@mecadetect.fr", Role.TECHNICIEN);
        technicienB = utilisateur("technicien-b.conc@mecadetect.fr", Role.TECHNICIEN);
        demandeur = utilisateur("demandeur.conc@mecadetect.fr", Role.DEMANDEUR);
        // Numéro fixe : hors transaction, on évite d'appeler la séquence dans une transaction en lecture seule
        intervention = interventionRepository.save(new Intervention("INT-CONC-000001",
                "Fuite d'huile", "Fuite sur le vérin principal", Priorite.HAUTE, equipement, demandeur, LUNDI_8H));
    }

    @AfterEach
    void nettoyer() {
        // Depuis le Sprint 4, le trigger écrit l'historique : il faut le supprimer avant l'intervention
        jdbc.update("DELETE FROM historique_statut WHERE id_intervention = ?", intervention.getId());
        interventionRepository.deleteById(intervention.getId());
        equipementRepository.deleteById(equipement.getId());
        typeRepository.deleteById(type.getId());
        zoneRepository.deleteById(zone.getId());
        for (var utilisateur : new Utilisateur[]{adminA, adminB, technicienA, technicienB, demandeur}) {
            utilisateurRepository.deleteById(utilisateur.getId());
        }
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.save(new Utilisateur("Test", "Concurrence", email, "hash", role));
    }

    @Test
    @DisplayName("Deux planifications simultanées : la seconde à valider échoue, la première est conservée")
    void deuxPlanificationsSimultanees() {
        var transactionA = new TransactionTemplate(transactionManager);
        var transactionB = new TransactionTemplate(transactionManager);
        transactionB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transactionA.executeWithoutResult(etatA -> {
            var vueDeA = interventionRepository.findById(intervention.getId()).orElseThrow();
            assertThat(vueDeA.getVersion()).isZero();

            // Pendant que A réfléchit, B planifie et valide
            transactionB.executeWithoutResult(etatB -> {
                var vueDeB = interventionRepository.findById(intervention.getId()).orElseThrow();
                vueDeB.planifier(technicienB, LUNDI_8H.plusDays(1), adminB);
            });

            // A planifie à son tour, sur une version qui n'existe plus en base
            vueDeA.planifier(technicienA, LUNDI_8H.plusDays(2), adminA);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        var enBase = interventionRepository.findById(intervention.getId()).orElseThrow();
        assertThat(enBase.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(enBase.getVersion()).isEqualTo(1L);
        assertThat(enBase.getDatePlanifiee()).isEqualTo(LUNDI_8H.plusDays(1));

        // La transaction perdante est annulée avec sa ligne d'historique : seule la planification de B reste
        assertThat(jdbc.queryForList(
                "SELECT nouveau_statut FROM historique_statut WHERE id_intervention = ? ORDER BY id_historique",
                String.class, intervention.getId()))
                .containsExactly("DEMANDEE", "PLANIFIEE");
    }
}