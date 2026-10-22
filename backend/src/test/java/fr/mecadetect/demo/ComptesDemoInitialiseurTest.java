package fr.mecadetect.demo;

import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Création des comptes de démonstration : seuls les comptes absents sont créés, en un seul enregistrement.
@ExtendWith(MockitoExtension.class)
class ComptesDemoInitialiseurTest {

    private static final String MOT_DE_PASSE_DEMO = "secret-de-test";
    private static final String HACHE = "hache-de-test";

    @Mock private UtilisateurRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Captor private ArgumentCaptor<List<Utilisateur>> enregistres;

    private ComptesDemoInitialiseur initialiseur;

    @BeforeEach
    void setUp() {
        initialiseur = new ComptesDemoInitialiseur(
                new ComptesDemoEnMemoire(), repository, passwordEncoder, MOT_DE_PASSE_DEMO);
    }

    private void comptesDejaPresents(Set<String> emails) {
        when(repository.existsByEmailIgnoreCase(anyString()))
                .thenAnswer(appel -> emails.contains(appel.<String>getArgument(0)));
    }

    @Test
    @DisplayName("Base vide : un compte par rôle est créé, avec le mot de passe haché")
    void baseVide() {
        comptesDejaPresents(Set.of());
        when(passwordEncoder.encode(MOT_DE_PASSE_DEMO)).thenReturn(HACHE);

        initialiseur.run(null);

        verify(repository).saveAll(enregistres.capture());
        assertThat(enregistres.getValue())
                .extracting(Utilisateur::getEmail, Utilisateur::getRole)
                .containsExactly(
                        tuple("admin@mecadetect.fr", Role.ADMIN),
                        tuple("technicien@mecadetect.fr", Role.TECHNICIEN),
                        tuple("demandeur@mecadetect.fr", Role.DEMANDEUR));
    }

    @Test
    @DisplayName("Un compte déjà présent n'est pas recréé")
    void compteDejaPresent() {
        comptesDejaPresents(Set.of("admin@mecadetect.fr"));
        when(passwordEncoder.encode(MOT_DE_PASSE_DEMO)).thenReturn(HACHE);

        initialiseur.run(null);

        verify(repository).saveAll(enregistres.capture());
        assertThat(enregistres.getValue())
                .extracting(Utilisateur::getEmail)
                .containsExactly("technicien@mecadetect.fr", "demandeur@mecadetect.fr");
    }

    @Test
    @DisplayName("Tous les comptes présents : rien n'est créé et aucun mot de passe n'est haché")
    void idempotent() {
        comptesDejaPresents(Set.of("admin@mecadetect.fr", "technicien@mecadetect.fr", "demandeur@mecadetect.fr"));

        initialiseur.run(null);

        verify(repository).saveAll(enregistres.capture());
        assertThat(enregistres.getValue()).isEmpty();
        verify(passwordEncoder, never()).encode(anyString());
    }
}