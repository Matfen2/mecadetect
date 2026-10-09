package fr.mecadetect.equipement;

import fr.mecadetect.erreur.DonneesInvalidesException;
import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.erreur.RessourceIntrouvableException;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EquipementService {

    private final EquipementRepository equipementRepository;
    private final ZoneRepository zoneRepository;
    private final TypeEquipementRepository typeRepository;

    public EquipementService(EquipementRepository equipementRepository,
                             ZoneRepository zoneRepository,
                             TypeEquipementRepository typeRepository) {
        this.equipementRepository = equipementRepository;
        this.zoneRepository = zoneRepository;
        this.typeRepository = typeRepository;
    }

    public Equipement creer(EquipementCommande commande) {
        verifierReferenceDisponible(commande.reference());

        var equipement = new Equipement(
                commande.reference(), commande.nom(), commande.dateMiseService(),
                chargerType(commande.idType()), chargerZone(commande.idZone()));

        return equipementRepository.save(equipement);
    }

    @Transactional(readOnly = true)
    public Equipement obtenir(Long idEquipement) {
        return equipementRepository.trouverAvecTypeEtZone(idEquipement)
                .orElseThrow(() -> introuvable(idEquipement));
    }

    public Equipement modifier(Long idEquipement, EquipementCommande commande) {
        var equipement = obtenir(idEquipement);

        boolean referenceModifiee = !equipement.getReference().equalsIgnoreCase(commande.reference());
        if (referenceModifiee) {
            verifierReferenceDisponible(commande.reference());
        }

        equipement.modifier(
                commande.reference(), commande.nom(), commande.dateMiseService(),
                chargerType(commande.idType()), chargerZone(commande.idZone()));
        return equipement;
    }

    public void archiver(Long idEquipement) {
        var equipement = equipementRepository.findById(idEquipement)
                .orElseThrow(() -> introuvable(idEquipement));
        equipement.archiver();
    }

    private void verifierReferenceDisponible(String reference) {
        if (equipementRepository.existsByReferenceIgnoreCase(reference.trim())) {
            throw new RegleMetierException("REFERENCE_DEJA_UTILISEE",
                    "La référence « " + reference + " » est déjà utilisée par un autre équipement.");
        }
    }

    private TypeEquipement chargerType(Long idType) {
        return typeRepository.findById(idType)
                .orElseThrow(() -> new DonneesInvalidesException("TYPE_INCONNU",
                        "Le type d'équipement " + idType + " n'existe pas."));
    }

    private Zone chargerZone(Long idZone) {
        return zoneRepository.findById(idZone)
                .orElseThrow(() -> new DonneesInvalidesException("ZONE_INCONNUE",
                        "La zone " + idZone + " n'existe pas."));
    }

    private static RessourceIntrouvableException introuvable(Long idEquipement) {
        return new RessourceIntrouvableException("EQUIPEMENT_INTROUVABLE",
                "L'équipement " + idEquipement + " n'existe pas.");
    }
}