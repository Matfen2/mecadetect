package fr.mecadetect.intervention.application;

import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.erreur.RessourceIntrouvableException;
import fr.mecadetect.intervention.domain.DemandePage;
import fr.mecadetect.intervention.domain.Habilitations;
import fr.mecadetect.intervention.domain.HistoriqueStatutRepository;
import fr.mecadetect.intervention.domain.InterventionRepository;
import fr.mecadetect.intervention.domain.ParcEquipements;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.domain.Utilisateurs;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Les cas d'usage se testent sans Spring ni base : la couche application ne dépend que des ports
/// du domaine, simulés ici. Les parcours complets restent couverts par les tests d'API.
@ExtendWith(MockitoExtension.class)
class InterventionServiceTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);

    @Mock private InterventionRepository interventions;
    @Mock private HistoriqueStatutRepository historiques;
    @Mock private ParcEquipements parcEquipements;
    @Mock private Utilisateurs utilisateurs;
    @Mock private Habilitations habilitations;

    private InterventionService service;

    @BeforeEach
    void setUp() {
        service = new InterventionService(interventions, historiques, parcEquipements, utilisateurs, habilitations,
                HORLOGE);
    }

    @Test
    @DisplayName("Une intervention inconnue est signalée comme introuvable")
    void interventionInconnue() {
        when(interventions.trouverDetail(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(42L))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("42");
        verifyNoInteractions(historiques);
    }

    @Test
    @DisplayName("L'historique d'un équipement inconnu est refusé avant toute recherche")
    void historiqueDUnEquipementInconnu() {
        when(parcEquipements.existe(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.historiqueEquipement(7L, DemandePage.sansTri(0, 20)))
                .isInstanceOf(RessourceIntrouvableException.class);
        verifyNoInteractions(interventions);
    }

    @Test
    @DisplayName("Un compte désactivé ne peut pas déclarer de panne")
    void declarationParUnCompteDesactive() {
        var desactive = new Utilisateur("Moreau", "Sophie", "demandeur@mecadetect.fr", "hash", Role.DEMANDEUR);
        desactive.desactiver();
        when(utilisateurs.trouver(3L)).thenReturn(Optional.of(desactive));

        assertThatThrownBy(() -> service.declarer(
                new DeclarationCommande(1L, "Fuite d'huile", "Fuite sur le vérin principal", Priorite.HAUTE), 3L))
                .isInstanceOf(ActionNonAutoriseeException.class);
        verify(utilisateurs).trouver(3L);
        verifyNoInteractions(parcEquipements);
        verify(interventions, never()).enregistrer(any());
    }
}
