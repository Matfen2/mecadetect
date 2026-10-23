package fr.mecadetect.demo;

import fr.mecadetect.utilisateur.Role;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/// Comptes de démonstration fictifs, un par rôle, définis en mémoire.
@Component
@Profile("demo")
public class ComptesDemoEnMemoire implements SourceComptesDemo {

    private static final List<CompteDemo> COMPTES = List.of(
            new CompteDemo("Martin", "Claire", "admin@mecadetect.fr", Role.ADMIN),
            new CompteDemo("Bernard", "Lucas", "technicien@mecadetect.fr", Role.TECHNICIEN),
            new CompteDemo("Moreau", "Sophie", "demandeur@mecadetect.fr", Role.DEMANDEUR)
    );

    @Override
    public List<CompteDemo> comptes() {
        return COMPTES;
    }
}