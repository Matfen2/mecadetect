package fr.mecadetect.equipement;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EquipementRepositoryTest {

    @Autowired
    private EquipementRepository equipementRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private TypeEquipementRepository typeRepository;

    @Autowired
    private EntityManager entityManager;

    private Zone zone;
    private TypeEquipement type;

    @BeforeEach
    void setUp() {
        zone = zoneRepository.saveAndFlush(new Zone("T1", "Zone de test"));
        type = typeRepository.saveAndFlush(new TypeEquipement("Type de test"));
    }

    private Equipement equipement(String reference) {
        return new Equipement(reference, "Équipement " + reference, LocalDate.of(2022, 1, 10), type, zone);
    }

    @Test
    @DisplayName("Un équipement est enregistré avec sa zone et son type")
    void enregistrement() {
        var enregistre = equipementRepository.saveAndFlush(equipement("TST-001"));

        assertThat(enregistre.getId()).isNotNull();
        assertThat(enregistre.getStatut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(enregistre.getZone().getCode()).isEqualTo("T1");
    }

    @Test
    @DisplayName("Deux équipements ne peuvent pas avoir la même référence (uk_equipement_reference)")
    void referenceUnique() {
        equipementRepository.saveAndFlush(equipement("TST-002"));

        assertThatThrownBy(() -> equipementRepository.saveAndFlush(equipement("TST-002")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByReferenceIgnoreCase détecte une référence déjà utilisée")
    void referenceDejaUtilisee() {
        equipementRepository.saveAndFlush(equipement("TST-003"));

        assertThat(equipementRepository.existsByReferenceIgnoreCase("tst-003")).isTrue();
        assertThat(equipementRepository.existsByReferenceIgnoreCase("TST-999")).isFalse();
    }

    @Test
    @DisplayName("La zone et le type sont chargés en différé (LAZY), pas avec l'équipement")
    void chargementDiffere() {
        var id = equipementRepository.saveAndFlush(equipement("TST-004")).getId();
        entityManager.clear();

        var recharge = equipementRepository.findById(id).orElseThrow();

        assertThat(Hibernate.isInitialized(recharge.getZone())).isFalse();
        assertThat(Hibernate.isInitialized(recharge.getType())).isFalse();
    }

    @Test
    @DisplayName("Le statut ARCHIVE est bien persisté (contrainte ck_equipement_statut respectée)")
    void archivagePersiste() {
        var equipement = equipementRepository.saveAndFlush(equipement("TST-005"));
        equipement.archiver();
        equipementRepository.flush();
        entityManager.clear();

        assertThat(equipementRepository.findById(equipement.getId()))
                .get().extracting(Equipement::getStatut).isEqualTo(StatutEquipement.ARCHIVE);
    }

    @Test
    @DisplayName("Les zones et types sont listés par ordre alphabétique")
    void referentielTrie() {
        zoneRepository.saveAndFlush(new Zone("A0", "Zone A"));
        typeRepository.saveAndFlush(new TypeEquipement("Aaa premier type"));

        assertThat(zoneRepository.findAllByOrderByCodeAsc()).first()
                .extracting(Zone::getCode).isEqualTo("A0");
        assertThat(typeRepository.findAllByOrderByLibelleAsc()).first()
                .extracting(TypeEquipement::getLibelle).isEqualTo("Aaa premier type");
    }
}