package fr.mecadetect.equipement;

import fr.mecadetect.erreur.DonneesInvalidesException;
import fr.mecadetect.erreur.RegleMetierException;
import fr.mecadetect.erreur.RessourceIntrouvableException;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.referentiel.TypeEquipementRepository;
import fr.mecadetect.referentiel.Zone;
import fr.mecadetect.referentiel.ZoneRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Gestion du parc d'équipements : création, recherche, modification et archivage.
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

    /// Crée un équipement en service.
    ///
    /// @param commande données de l'équipement
    /// @return l'équipement enregistré
    /// @throws RegleMetierException      si la référence est déjà utilisée
    /// @throws DonneesInvalidesException si le type ou la zone n'existe pas
    public Equipement creer(EquipementCommande commande) {
        verifierReferenceDisponible(commande.reference());

        var equipement = new Equipement(
                commande.reference(), commande.nom(), commande.dateMiseService(),
                chargerType(commande.idType()), chargerZone(commande.idZone()));

        return equipementRepository.save(equipement);
    }

    /// Recherche paginée et filtrée ; zone et type sont chargés par jointure (pas de N+1).
    ///
    /// @param criteres filtres, tous facultatifs
    /// @param pageable page, taille et tri demandés
    /// @return la page d'équipements
    @Transactional(readOnly = true)
    public Page<Equipement> rechercher(CriteresRechercheEquipement criteres, Pageable pageable) {
        return equipementRepository.findAll(EquipementSpecifications.selon(criteres), pageable);
    }

    /// Charge un équipement avec son type et sa zone.
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @return l'équipement
    /// @throws RessourceIntrouvableException si l'équipement n'existe pas
    @Transactional(readOnly = true)
    public Equipement obtenir(Long idEquipement) {
        return charger(idEquipement);
    }

    /// Modifie les informations d'un équipement.
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @param commande     nouvelles données
    /// @return l'équipement modifié
    /// @throws RessourceIntrouvableException si l'équipement n'existe pas
    /// @throws RegleMetierException          si la nouvelle référence est déjà utilisée ou si l'équipement est archivé
    /// @throws DonneesInvalidesException     si le type ou la zone n'existe pas
    public Equipement modifier(Long idEquipement, EquipementCommande commande) {
        // Pas d'appel à obtenir() : depuis la classe elle-même, son @Transactional(readOnly = true)
        // serait ignoré (pas de proxy Spring) et laisserait croire à une lecture seule
        var equipement = charger(idEquipement);

        boolean referenceModifiee = !equipement.getReference().equalsIgnoreCase(commande.reference());
        if (referenceModifiee) {
            verifierReferenceDisponible(commande.reference());
        }

        equipement.modifier(
                commande.reference(), commande.nom(), commande.dateMiseService(),
                chargerType(commande.idType()), chargerZone(commande.idZone()));
        return equipement;
    }

    /// Archive un équipement (suppression logique).
    ///
    /// @param idEquipement identifiant de l'équipement
    /// @throws RessourceIntrouvableException si l'équipement n'existe pas
    /// @throws RegleMetierException          si l'équipement est déjà archivé
    public void archiver(Long idEquipement) {
        var equipement = equipementRepository.findById(idEquipement)
                .orElseThrow(() -> introuvable(idEquipement));
        equipement.archiver();
    }

    /// Équipement avec son type et sa zone, chargés par jointure.
    private Equipement charger(Long idEquipement) {
        return equipementRepository.trouverAvecTypeEtZone(idEquipement)
                .orElseThrow(() -> introuvable(idEquipement));
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
                        inexistant("Le type d'équipement", idType)));
    }

    private Zone chargerZone(Long idZone) {
        return zoneRepository.findById(idZone)
                .orElseThrow(() -> new DonneesInvalidesException("ZONE_INCONNUE",
                        inexistant("La zone", idZone)));
    }

    private static RessourceIntrouvableException introuvable(Long idEquipement) {
        return new RessourceIntrouvableException("EQUIPEMENT_INTROUVABLE",
                inexistant("L'équipement", idEquipement));
    }

    /// Message commun aux éléments introuvables : « La zone 12 n'existe pas. »
    private static String inexistant(String element, Long id) {
        return element + " " + id + " n'existe pas.";
    }
}