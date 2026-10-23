package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.application.InterventionService;
import fr.mecadetect.intervention.domain.Habilitations;
import fr.mecadetect.intervention.domain.HistoriqueStatutRepository;
import fr.mecadetect.intervention.domain.InterventionRepository;
import fr.mecadetect.intervention.domain.ParcEquipements;
import fr.mecadetect.intervention.domain.Utilisateurs;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Clock;
import java.util.List;

/// Assemble les cas d'usage des interventions avec Spring, sans que la couche application le sache.
///
/// [InterventionService] est une classe Java sans annotation : c'est ici qu'elle devient un bean,
/// et que ses méthodes reçoivent leurs transactions. Les règles sont celles qu'exprimait
/// `@Transactional` : lecture seule pour les consultations, transaction en écriture pour le reste,
/// annulation sur toute exception non vérifiée.
@Configuration
class ConfigurationInterventions {

    /// Méthodes de consultation : transaction en lecture seule.
    static final List<String> METHODES_LECTURE =
            List.of("obtenir", "rechercher", "aTraiterPar", "historiqueEquipement");

    @Bean
    InterventionService interventionService(InterventionRepository interventions,
                                            HistoriqueStatutRepository historiques,
                                            ParcEquipements equipements,
                                            Utilisateurs utilisateurs,
                                            Habilitations habilitations,
                                            Clock clock,
                                            PlatformTransactionManager transactions) {
        var service = new InterventionService(interventions, historiques, equipements, utilisateurs,
                habilitations, clock);
        return transactionnel(service, transactions);
    }

    /// Entoure le service d'un proxy qui ouvre une transaction autour de chaque méthode publique.
    static InterventionService transactionnel(InterventionService service, PlatformTransactionManager transactions) {
        var lecture = new RuleBasedTransactionAttribute();
        lecture.setReadOnly(true);
        var ecriture = new RuleBasedTransactionAttribute();

        var regles = new NameMatchTransactionAttributeSource();
        METHODES_LECTURE.forEach(methode -> regles.addTransactionalMethod(methode, lecture));
        regles.addTransactionalMethod("*", ecriture);

        var proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, regles));
        return (InterventionService) proxy.getProxy();
    }
}
