package fr.mecadetect.utilisateur;

import fr.mecadetect.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration sur une vraie base Oracle (Testcontainers).
 * Chaque test est annulé en fin d'exécution grâce à @Transactional.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UtilisateurRepositoryTest {

    @Autowired
    private UtilisateurRepository repository;

    @Test
    @DisplayName("Un utilisateur enregistré est retrouvé par son email, sans tenir compte de la casse")
    void trouveParEmailSansTenirCompteDeLaCasse() {
        repository.saveAndFlush(new Utilisateur("Durand", "Alice", "alice.durand@mecadetect.fr", "hash", Role.TECHNICIEN));

        var trouve = repository.findByEmailIgnoreCase("ALICE.DURAND@mecadetect.fr");

        assertThat(trouve).isPresent();
        assertThat(trouve.get().getId()).isNotNull();
        assertThat(trouve.get().getRole()).isEqualTo(Role.TECHNICIEN);
    }

    @Test
    @DisplayName("Un nouvel utilisateur est actif par défaut")
    void nouvelUtilisateurEstActif() {
        var utilisateur = repository.saveAndFlush(
                new Utilisateur("Martin", "Paul", "paul.martin@mecadetect.fr", "hash", Role.DEMANDEUR));

        assertThat(utilisateur.isActif()).isTrue();
    }

    @Test
    @DisplayName("Un utilisateur désactivé reste en base mais n'est plus actif")
    void desactiverUnUtilisateur() {
        var utilisateur = repository.saveAndFlush(
                new Utilisateur("Petit", "Lea", "lea.petit@mecadetect.fr", "hash", Role.ADMIN));

        utilisateur.desactiver();
        repository.flush();

        assertThat(repository.findById(utilisateur.getId()))
                .get()
                .extracting(Utilisateur::isActif)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("Deux utilisateurs ne peuvent pas avoir le même email (contrainte uk_utilisateur_email)")
    void emailUnique() {
        repository.saveAndFlush(new Utilisateur("Roux", "Jean", "jean.roux@mecadetect.fr", "hash", Role.TECHNICIEN));

        assertThatThrownBy(() -> repository.saveAndFlush(
                new Utilisateur("Roux", "Julie", "jean.roux@mecadetect.fr", "hash", Role.DEMANDEUR)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByEmailIgnoreCase détecte un email déjà utilisé")
    void emailDejaUtilise() {
        repository.saveAndFlush(new Utilisateur("Blanc", "Hugo", "hugo.blanc@mecadetect.fr", "hash", Role.TECHNICIEN));

        assertThat(repository.existsByEmailIgnoreCase("Hugo.Blanc@mecadetect.fr")).isTrue();
        assertThat(repository.existsByEmailIgnoreCase("inconnu@mecadetect.fr")).isFalse();
    }
}