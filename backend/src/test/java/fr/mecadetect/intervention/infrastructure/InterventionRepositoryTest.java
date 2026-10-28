package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.TestPersistanceOracle;
import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.intervention.domain.Intervention;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.domain.StatutIntervention;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import fr.mecadetect.utilisateur.Habilitation;
import fr.mecadetect.utilisateur.HabilitationId;
import fr.mecadetect.utilisateur.HabilitationRepository;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Persistance des interventions : mapping, séquence, verrou optimiste et contraintes CHECK.
@TestPersistanceOracle
class InterventionRepositoryTest {

    private static final LocalDateTime LUNDI_8H = LocalDateTime.of(2026, 10, 12, 8, 0);

    @Autowired private InterventionJpaRepository interventionRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private EquipementRepository equipementRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EntityManager entityManager;

    private TypeEquipement type;
    private Equipement equipement;
    private Utilisateur demandeur;
    private Utilisateur admin;
    private Utilisateur technicien;

    @BeforeEach
    void setUp() {
        var zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
        equipement = equipementRepository.saveAndFlush(
                new Equipement("TST-001", "Équipement de test", LocalDate.of(2022, 1, 10), type, zone));
        demandeur = utilisateur("demandeur.it@mecadetect.fr", Role.DEMANDEUR);
        admin = utilisateur("admin.it@mecadetect.fr", Role.ADMIN);
        technicien = utilisateur("technicien.it@mecadetect.fr", Role.TECHNICIEN);
    }

    private Utilisateur utilisateur(String email, Role role) {
        return utilisateurRepository.saveAndFlush(new Utilisateur("Test", "Utilisateur", email, "hash", role));
    }

    private Intervention nouvelleIntervention() {
        return new Intervention(interventionRepository.prochainNumero(2026), "Fuite d'huile",
                "Fuite sur le vérin principal", Priorite.HAUTE, equipement, demandeur, LUNDI_8H);
    }

    @Test
    @DisplayName("Une intervention est enregistrée avec son numéro, sa version et ses relations")
    void enregistrement() {
        var enregistree = interventionRepository.saveAndFlush(nouvelleIntervention());
        entityManager.clear();

        var relue = interventionRepository.findById(enregistree.getId()).orElseThrow();
        assertThat(relue.getNumero()).matches("INT-2026-\\d{6}");
        assertThat(relue.getStatut()).isEqualTo(StatutIntervention.DEMANDEE);
        assertThat(relue.getVersion()).isZero();
        assertThat(relue.getEquipement().getReference()).isEqualTo("TST-001");
        assertThat(relue.getDemandeur().getEmail()).isEqualTo("demandeur.it@mecadetect.fr");
    }

    @Test
    @DisplayName("La séquence fournit des numéros différents et croissants")
    void numerosSuccessifs() {
        var premier = interventionRepository.prochainNumero(2026);
        var second = interventionRepository.prochainNumero(2026);

        assertThat(second).isNotEqualTo(premier);
        assertThat(second.compareTo(premier)).isPositive();
    }

    @Test
    @DisplayName("UC-DB6 : la version est incrémentée à chaque modification")
    void versionIncrementee() {
        var intervention = interventionRepository.saveAndFlush(nouvelleIntervention());
        assertThat(intervention.getVersion()).isZero();

        intervention.planifier(technicien, LUNDI_8H.plusDays(1), admin);
        interventionRepository.flush();

        assertThat(intervention.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("RG-04 : la requête détecte une intervention EN_COURS du technicien")
    void interventionEnCoursDuTechnicien() {
        var intervention = interventionRepository.saveAndFlush(nouvelleIntervention());
        assertThat(interventionRepository.existsByTechnicienIdAndStatut(technicien.getId(), StatutIntervention.EN_COURS))
                .isFalse();

        intervention.planifier(technicien, LUNDI_8H, admin);
        intervention.demarrer(technicien, LUNDI_8H);
        interventionRepository.flush();

        assertThat(interventionRepository.existsByTechnicienIdAndStatut(technicien.getId(), StatutIntervention.EN_COURS))
                .isTrue();
        assertThat(interventionRepository.existsByEquipementIdAndStatutIn(equipement.getId(), StatutIntervention.OUVERTS))
                .isTrue();
    }

    @Test
    @DisplayName("RG-09 : une habilitation est enregistrée et retrouvée par sa clé composée")
    void habilitation() {
        habilitationRepository.saveAndFlush(new Habilitation(type, technicien, LocalDate.of(2025, 1, 6)));
        var autreType = typeRepository.saveAndFlush(new TypeEquipement("Autre type de test"));

        assertThat(habilitationRepository.existsById(new HabilitationId(type.getId(), technicien.getId()))).isTrue();
        assertThat(habilitationRepository.existsById(new HabilitationId(autreType.getId(), technicien.getId()))).isFalse();
    }

    @Test
    @DisplayName("La base refuse une intervention planifiée sans technicien (ck_intervention_technicien)")
    void contrainteCheckDeLaBase() {
        var intervention = interventionRepository.saveAndFlush(nouvelleIntervention());

        // Contournement volontaire du code Java : seule la base peut encore protéger la donnée
        assertThatThrownBy(() -> entityManager
                .createNativeQuery("UPDATE intervention SET statut = 'PLANIFIEE' WHERE id_intervention = :id")
                .setParameter("id", intervention.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasStackTraceContaining("CK_INTERVENTION_TECHNICIEN");
    }
}