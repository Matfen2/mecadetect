package fr.mecadetect.demo;

import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.utilisateur.Habilitation;
import fr.mecadetect.utilisateur.HabilitationId;
import fr.mecadetect.utilisateur.HabilitationRepository;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/// Habilitations du technicien de démonstration (profil "demo").
/// Lancé quand l'application est prête, donc après la création des comptes et du référentiel.
/// Le technicien n'est volontairement pas habilité sur les compresseurs et les chariots :
/// la règle RG-09 peut ainsi être montrée en démonstration.
@Component
@Profile("demo")
@RequiredArgsConstructor
public class HabilitationsDemoInitialiseur {

    private static final Logger LOG = LoggerFactory.getLogger(HabilitationsDemoInitialiseur.class);

    static final String TECHNICIEN = "technicien@mecadetect.fr";
    static final List<String> TYPES_HABILITES = List.of(
            "Presse hydraulique", "Tour CN", "Centre d'usinage", "Robot de soudure", "Convoyeur");

    private final UtilisateurRepository utilisateurRepository;
    private final TypeEquipementRepository typeRepository;
    private final HabilitationRepository habilitationRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialiser() {
        var technicien = utilisateurRepository.findByEmailIgnoreCase(TECHNICIEN).orElse(null);
        if (technicien == null) {
            LOG.warn("Technicien de démonstration absent : aucune habilitation créée.");
            return;
        }
        int creees = 0;
        for (var type : typeRepository.findAll()) {
            boolean habilite = TYPES_HABILITES.contains(type.getLibelle());
            if (habilite && !habilitationRepository.existsById(new HabilitationId(type.getId(), technicien.getId()))) {
                habilitationRepository.save(new Habilitation(type, technicien, LocalDate.of(2024, 9, 2)));
                creees++;
            }
        }
        if (creees > 0) {
            LOG.info("Habilitations de démonstration créées : {}", creees);
        }
    }
}