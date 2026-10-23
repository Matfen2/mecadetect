package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.erreur.ActionNonAutoriseeException;
import fr.mecadetect.erreur.RessourceIntrouvableException;
import fr.mecadetect.intervention.application.DeclarationCommande;
import fr.mecadetect.intervention.application.InterventionService;
import fr.mecadetect.intervention.domain.Habilitations;
import fr.mecadetect.intervention.domain.HistoriqueStatutRepository;
import fr.mecadetect.intervention.domain.InterventionRepository;
import fr.mecadetect.intervention.domain.ParcEquipements;
import fr.mecadetect.intervention.domain.Priorite;
import fr.mecadetect.intervention.domain.Utilisateurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Les transactions déclarées dans la configuration remplacent `@Transactional` :
/// lecture seule pour les consultations, écriture pour le reste, annulation sur exception.
@ExtendWith(MockitoExtension.class)
class ConfigurationInterventionsTest {

    @Mock private InterventionRepository interventions;
    @Mock private HistoriqueStatutRepository historiques;
    @Mock private ParcEquipements parcEquipements;
    @Mock private Utilisateurs utilisateurs;
    @Mock private Habilitations habilitations;
    @Mock private PlatformTransactionManager transactions;
    @Captor private ArgumentCaptor<TransactionDefinition> definition;

    private InterventionService service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = ConfigurationInterventions.transactionnel(
                new InterventionService(interventions, historiques, parcEquipements, utilisateurs, habilitations,
                        Clock.systemUTC()),
                transactions);
    }

    @Test
    @DisplayName("Une consultation s'exécute dans une transaction en lecture seule")
    void consultationEnLectureSeule() {
        when(interventions.trouverDetail(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(1L)).isInstanceOf(RessourceIntrouvableException.class);

        verify(transactions).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    @Test
    @DisplayName("Une action s'exécute dans une transaction en écriture, annulée en cas d'erreur")
    void actionEnEcriture() {
        when(utilisateurs.trouver(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.declarer(
                new DeclarationCommande(1L, "Fuite d'huile", "Fuite sur le vérin principal", Priorite.HAUTE), 3L))
                .isInstanceOf(ActionNonAutoriseeException.class);

        verify(transactions).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isFalse();
        verify(transactions).rollback(any());
    }
}
