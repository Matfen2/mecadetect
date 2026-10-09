package fr.mecadetect.equipement;

import fr.mecadetect.api.dto.EquipementDto;
import fr.mecadetect.api.dto.EquipementRequestDto;
import fr.mecadetect.api.dto.PageEquipementDto;
import fr.mecadetect.api.dto.StatutEquipementDto;
import fr.mecadetect.referentiel.ReferentielMapper;
import org.springframework.data.domain.Page;

/** Conversion entre l'entité et les DTO générés depuis le contrat. */
public final class EquipementMapper {

    private EquipementMapper() {
    }

    public static EquipementDto versDto(Equipement equipement) {
        return new EquipementDto()
                .idEquipement(equipement.getId())
                .reference(equipement.getReference())
                .nom(equipement.getNom())
                .dateMiseService(equipement.getDateMiseService())
                .statut(StatutEquipementDto.fromValue(equipement.getStatut().name()))
                .type(ReferentielMapper.versDto(equipement.getType()))
                .zone(ReferentielMapper.versDto(equipement.getZone()));
    }

    public static PageEquipementDto versPageDto(Page<Equipement> page) {
        return new PageEquipementDto()
                .page(page.getNumber())
                .taille(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .contenu(page.getContent().stream().map(EquipementMapper::versDto).toList());
    }

    public static EquipementCommande versCommande(EquipementRequestDto requete) {
        return new EquipementCommande(
                requete.getReference(),
                requete.getNom(),
                requete.getDateMiseService(),
                requete.getIdType(),
                requete.getIdZone());
    }
}