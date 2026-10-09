package fr.mecadetect.demo;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.equipement.EquipementRepository;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crée un parc d'équipements réaliste pour la démonstration (profil "demo" uniquement) :
 * un site industriel avec ses ateliers, ses machines et quelques équipements réformés.
 * Idempotent : un élément déjà présent n'est ni recréé ni modifié.
 */
@Component
@Profile("demo")
public class DonneesDemoReferentielInitialiseur implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DonneesDemoReferentielInitialiseur.class);

    static final List<ZoneDemo> ZONES = List.of(
            new ZoneDemo("A1", "Atelier A - Emboutissage"),
            new ZoneDemo("B1", "Atelier B - Usinage"),
            new ZoneDemo("C1", "Atelier C - Assemblage"),
            new ZoneDemo("L1", "Logistique - Quai d'expédition"),
            new ZoneDemo("U1", "Utilités - Local technique"));

    static final List<String> TYPES = List.of(
            "Presse hydraulique",
            "Tour CN",
            "Centre d'usinage",
            "Robot de soudure",
            "Convoyeur",
            "Compresseur d'air",
            "Chariot élévateur");

    static final List<EquipementDemo> EQUIPEMENTS = List.of(
            new EquipementDemo("PRS-001", "Presse 250 tonnes", "Presse hydraulique", "A1", LocalDate.of(2016, 3, 14), false),
            new EquipementDemo("PRS-002", "Presse 400 tonnes", "Presse hydraulique", "A1", LocalDate.of(2019, 9, 2), false),
            new EquipementDemo("PRS-003", "Presse de découpe 100 tonnes", "Presse hydraulique", "A1", LocalDate.of(2021, 1, 18), false),
            new EquipementDemo("PRS-004", "Presse 150 tonnes (réformée)", "Presse hydraulique", "A1", LocalDate.of(2004, 6, 7), true),
            new EquipementDemo("TCN-001", "Tour CN bi-broche", "Tour CN", "B1", LocalDate.of(2018, 4, 23), false),
            new EquipementDemo("TCN-002", "Tour CN 3 axes", "Tour CN", "B1", LocalDate.of(2020, 11, 9), false),
            new EquipementDemo("TCN-003", "Tour conventionnel (réformé)", "Tour CN", "B1", LocalDate.of(1998, 2, 16), true),
            new EquipementDemo("CUS-001", "Centre d'usinage 5 axes", "Centre d'usinage", "B1", LocalDate.of(2022, 5, 30), false),
            new EquipementDemo("CUS-002", "Centre d'usinage vertical", "Centre d'usinage", "B1", LocalDate.of(2017, 10, 11), false),
            new EquipementDemo("ROB-001", "Robot de soudure ligne 1", "Robot de soudure", "C1", LocalDate.of(2019, 3, 4), false),
            new EquipementDemo("ROB-002", "Robot de soudure ligne 2", "Robot de soudure", "C1", LocalDate.of(2019, 3, 4), false),
            new EquipementDemo("ROB-003", "Robot de soudure ligne 3", "Robot de soudure", "C1", LocalDate.of(2023, 7, 17), false),
            new EquipementDemo("CNV-001", "Convoyeur d'assemblage principal", "Convoyeur", "C1", LocalDate.of(2015, 8, 24), false),
            new EquipementDemo("CNV-002", "Convoyeur d'expédition", "Convoyeur", "L1", LocalDate.of(2020, 2, 3), false),
            new EquipementDemo("CNV-003", "Convoyeur de tri", "Convoyeur", "L1", LocalDate.of(2024, 4, 15), false),
            new EquipementDemo("CMP-001", "Compresseur à vis 75 kW", "Compresseur d'air", "U1", LocalDate.of(2018, 12, 10), false),
            new EquipementDemo("CMP-002", "Compresseur de secours", "Compresseur d'air", "U1", LocalDate.of(2012, 5, 21), false),
            new EquipementDemo("CHE-001", "Chariot élévateur électrique 2,5 t", "Chariot élévateur", "L1", LocalDate.of(2021, 6, 28), false),
            new EquipementDemo("CHE-002", "Chariot élévateur électrique 1,6 t", "Chariot élévateur", "L1", LocalDate.of(2023, 9, 5), false),
            new EquipementDemo("CHE-003", "Chariot thermique (réformé)", "Chariot élévateur", "L1", LocalDate.of(2007, 11, 30), true));

    private final ZoneRepository zoneRepository;
    private final TypeEquipementRepository typeRepository;
    private final EquipementRepository equipementRepository;

    public DonneesDemoReferentielInitialiseur(ZoneRepository zoneRepository,
                                              TypeEquipementRepository typeRepository,
                                              EquipementRepository equipementRepository) {
        this.zoneRepository = zoneRepository;
        this.typeRepository = typeRepository;
        this.equipementRepository = equipementRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Zone> zones = creerZones();
        Map<String, TypeEquipement> types = creerTypes();

        int crees = 0;
        for (EquipementDemo demo : EQUIPEMENTS) {
            if (equipementRepository.existsByReferenceIgnoreCase(demo.reference())) {
                continue;
            }
            var equipement = new Equipement(demo.reference(), demo.nom(), demo.dateMiseService(),
                    types.get(demo.type()), zones.get(demo.codeZone()));
            if (demo.archive()) {
                equipement.archiver();
            }
            equipementRepository.save(equipement);
            crees++;
        }
        LOG.info("Données de démonstration : {} zones, {} types, {} équipement(s) créé(s)",
                zones.size(), types.size(), crees);
    }

    private Map<String, Zone> creerZones() {
        Map<String, Zone> parCode = new HashMap<>();
        zoneRepository.findAll().forEach(zone -> parCode.put(zone.getCode(), zone));
        for (ZoneDemo demo : ZONES) {
            parCode.computeIfAbsent(demo.code(), code -> zoneRepository.save(new Zone(code, demo.libelle())));
        }
        return parCode;
    }

    private Map<String, TypeEquipement> creerTypes() {
        Map<String, TypeEquipement> parLibelle = new HashMap<>();
        typeRepository.findAll().forEach(type -> parLibelle.put(type.getLibelle(), type));
        for (String libelle : TYPES) {
            parLibelle.computeIfAbsent(libelle, l -> typeRepository.save(new TypeEquipement(l)));
        }
        return parLibelle;
    }

    record ZoneDemo(String code, String libelle) {
    }

    record EquipementDemo(String reference, String nom, String type, String codeZone,
                          LocalDate dateMiseService, boolean archive) {
    }
}