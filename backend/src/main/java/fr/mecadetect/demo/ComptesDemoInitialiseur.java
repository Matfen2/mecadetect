package fr.mecadetect.demo;

import fr.mecadetect.utilisateur.Role;
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

/**
 * Crée un compte par rôle au démarrage de l'application, uniquement avec le profil "demo".
 * L'opération est idempotente : un compte déjà présent n'est ni recréé ni modifié.
 */
@Component
@Profile("demo")
public class ComptesDemoInitialiseur implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComptesDemoInitialiseur.class);

    static final List<CompteDemo> COMPTES = List.of(
            new CompteDemo("Martin", "Claire", "admin@mecadetect.fr", Role.ADMIN),
            new CompteDemo("Bernard", "Lucas", "technicien@mecadetect.fr", Role.TECHNICIEN),
            new CompteDemo("Moreau", "Sophie", "demandeur@mecadetect.fr", Role.DEMANDEUR)
    );

    private final UtilisateurRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String motDePasse;

    public ComptesDemoInitialiseur(UtilisateurRepository repository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${mecadetect.demo.mot-de-passe}") String motDePasse) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.motDePasse = motDePasse;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int crees = 0;
        for (CompteDemo compte : COMPTES) {
            if (!repository.existsByEmailIgnoreCase(compte.email())) {
                repository.save(new Utilisateur(
                        compte.nom(), compte.prenom(), compte.email(),
                        passwordEncoder.encode(motDePasse), compte.role()));
                crees++;
            }
        }
        LOG.info("Comptes de démonstration : {} créé(s), {} déjà présent(s). Emails : {}",
                crees, COMPTES.size() - crees,
                COMPTES.stream().map(CompteDemo::email).toList());
    }

    record CompteDemo(String nom, String prenom, String email, Role role) {
    }
}