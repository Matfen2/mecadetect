package fr.mecadetect.technicien;

import fr.mecadetect.intervention.domain.ChargeTechnicien;
import fr.mecadetect.intervention.domain.InterventionRepository;
import fr.mecadetect.intervention.domain.InterventionResume;
import fr.mecadetect.intervention.domain.StatutIntervention;
import fr.mecadetect.referentiel.TypeEquipement;
import fr.mecadetect.utilisateur.Habilitation;
import fr.mecadetect.utilisateur.HabilitationRepository;
import fr.mecadetect.utilisateur.Role;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Liste des techniciens pour l'écran de planification.
/// Quatre requêtes quel que soit le nombre de techniciens (pas de N+1) :
/// techniciens, habilitations, charge groupée, interventions en cours.
@Service
@Transactional(readOnly = true)
public class TechnicienService {

    private static final Set<StatutIntervention> OUVERTES =
            Set.of(StatutIntervention.PLANIFIEE, StatutIntervention.EN_COURS);

    private final UtilisateurRepository utilisateurRepository;
    private final HabilitationRepository habilitationRepository;
    private final InterventionRepository interventionRepository;

    public TechnicienService(UtilisateurRepository utilisateurRepository,
                             HabilitationRepository habilitationRepository,
                             InterventionRepository interventionRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.habilitationRepository = habilitationRepository;
        this.interventionRepository = interventionRepository;
    }

    /// Techniciens actifs, avec leurs habilitations et leur charge, par ordre alphabétique.
    ///
    /// @param idType si renseigné, seuls les techniciens habilités sur ce type sont retournés (RG-09)
    /// @return les techniciens et leur charge
    public List<TechnicienVue> lister(Long idType) {
        var techniciens = utilisateurRepository.findByRoleAndActifTrueOrderByNomAscPrenomAsc(Role.TECHNICIEN);
        if (techniciens.isEmpty()) {
            return List.of();
        }
        var ids = techniciens.stream().map(Utilisateur::getId).toList();

        Map<Long, List<TypeEquipement>> habilitations = habilitationRepository.findByTechnicienIdIn(ids).stream()
                .collect(Collectors.groupingBy(h -> h.getTechnicien().getId(),
                        Collectors.mapping(Habilitation::getType, Collectors.toList())));
        Map<Long, Long> charge = interventionRepository.chargeParTechnicien(OUVERTES).stream()
                .collect(Collectors.toMap(ChargeTechnicien::idTechnicien, ChargeTechnicien::nombre));
        Map<Long, InterventionResume> enCours = interventionRepository.enCours().stream()
                .collect(Collectors.toMap(InterventionResume::idTechnicien, Function.identity(), (a, _) -> a));

        return techniciens.stream()
                .map(t -> new TechnicienVue(t,
                        habilitations.getOrDefault(t.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(TypeEquipement::getLibelle))
                                .toList(),
                        charge.getOrDefault(t.getId(), 0L).intValue(),
                        enCours.get(t.getId())))
                .filter(vue -> idType == null
                        || vue.habilitations().stream().anyMatch(type -> type.getId().equals(idType)))
                .toList();
    }
}