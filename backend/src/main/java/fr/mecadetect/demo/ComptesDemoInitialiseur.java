package fr.mecadetect.demo;

import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/// Crée les comptes de démonstration au démarrage de l'application, uniquement avec le profil `demo`.
///
/// L'opération est idempotente : un compte déjà présent n'est ni recréé ni modifié.
@Component
@Profile("demo")
public class ComptesDemoInitialiseur implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComptesDemoInitialiseur.class);

    private final SourceComptesDemo source;
    private final UtilisateurRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String motDePasse;

    public ComptesDemoInitialiseur(SourceComptesDemo source,
                                   UtilisateurRepository repository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${mecadetect.demo.mot-de-passe}") String motDePasse) {
        this.source = source;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.motDePasse = motDePasse;
    }

    /// Enregistre, en une seule fois, les comptes de la source absents de la base.
    ///
    /// @param args arguments de la ligne de commande, non utilisés
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<CompteDemo> comptes = source.comptes();

        List<Utilisateur> nouveaux = comptes.stream()
                .filter(compte -> !repository.existsByEmailIgnoreCase(compte.email()))
                .map(this::versUtilisateur)
                .toList();
        repository.saveAll(nouveaux);

        if (LOG.isInfoEnabled()) {
            LOG.info("Comptes de démonstration : {} créé(s), {} déjà présent(s). Emails : {}",
                    nouveaux.size(), comptes.size() - nouveaux.size(),
                    comptes.stream().map(CompteDemo::email).toList());
        }
    }

    private Utilisateur versUtilisateur(CompteDemo compte) {
        return new Utilisateur(compte.nom(), compte.prenom(), compte.email(),
                passwordEncoder.encode(motDePasse), compte.role());
    }
}