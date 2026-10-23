package fr.mecadetect.demo;

import fr.mecadetect.TestcontainersConfiguration;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.utilisateur.HabilitationId;
import fr.mecadetect.utilisateur.HabilitationRepository;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Avec le profil "demo", le technicien est habilité sur une partie du parc (RG-09). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class HabilitationsDemoTest {

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private TypeEquipementRepository typeRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private HabilitationsDemoInitialiseur initialiseur;

    private boolean estHabilite(String libelleType) {
        var technicien = utilisateurRepository.findByEmailIgnoreCase(HabilitationsDemoInitialiseur.TECHNICIEN).orElseThrow();
        var type = typeRepository.findAll().stream()
                .filter(t -> t.getLibelle().equals(libelleType))
                .map(TypeEquipement::getId)
                .findFirst().orElseThrow();
        return habilitationRepository.existsById(new HabilitationId(type, technicien.getId()));
    }

    @Test
    @DisplayName("Le technicien est habilité sur les presses, pas sur les chariots élévateurs")
    void habilitationsCreees() {
        assertThat(estHabilite("Presse hydraulique")).isTrue();
        assertThat(estHabilite("Robot de soudure")).isTrue();
        assertThat(estHabilite("Chariot élévateur")).isFalse();
    }

    @Test
    @DisplayName("Relancer l'initialisation ne crée pas de doublon")
    void initialisationIdempotente() {
        long avant = habilitationRepository.count();

        initialiseur.initialiser();

        assertThat(habilitationRepository.count()).isEqualTo(avant);
    }
}