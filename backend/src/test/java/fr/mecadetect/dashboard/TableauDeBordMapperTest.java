package fr.mecadetect.dashboard;

import fr.mecadetect.api.dto.DashboardDto;
import fr.mecadetect.api.dto.DashboardEvolutionMensuelleInnerDto;
import fr.mecadetect.api.dto.DashboardOuvertesParPrioriteInnerDto;
import fr.mecadetect.api.dto.PrioriteDto;
import fr.mecadetect.api.dto.RoleDto;
import fr.mecadetect.intervention.Priorite;
import fr.mecadetect.mapping.ConversionsCommunes;
import fr.mecadetect.reporting.ChargeTechnicienPeriode;
import fr.mecadetect.reporting.EvolutionMensuelle;
import fr.mecadetect.reporting.TopEquipement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// Mapper du tableau de bord, généré par MapStruct : le contexte Spring ne contient que
/// l'implémentation générée et ses conversions (ni base, ni web).
@SpringJUnitConfig(classes = {TableauDeBordMapperImpl.class, ConversionsCommunes.class})
class TableauDeBordMapperTest {

    @Autowired
    private TableauDeBordMapper mapper;

    private static TableauDeBord tableau(long equipementsEnPanne) {
        SequencedMap<Priorite, Long> ouvertes = new LinkedHashMap<>();
        ouvertes.put(Priorite.CRITIQUE, 2L);
        ouvertes.put(Priorite.HAUTE, 0L);
        ouvertes.put(Priorite.NORMALE, 5L);
        ouvertes.put(Priorite.BASSE, 1L);
        return new TableauDeBord(ouvertes, equipementsEnPanne, 12.5,
                List.of(new TopEquipement(1, "PRS-001", "Presse", 7L)),
                List.of(new EvolutionMensuelle(YearMonth.of(2026, 9), 10L, null),
                        new EvolutionMensuelle(YearMonth.of(2026, 10), 4L, -6L)),
                List.of(new ChargeTechnicienPeriode(3L, "Lucas", "Bernard", 2L, 9L, 540L)));
    }

    @Test
    @DisplayName("Les priorités gardent l'ordre d'urgence du service, avec leur compteur")
    void ouvertesParPriorite() {
        DashboardDto dto = mapper.versDto(tableau(4));

        assertThat(dto.getOuvertesParPriorite())
                .extracting(DashboardOuvertesParPrioriteInnerDto::getPriorite, DashboardOuvertesParPrioriteInnerDto::getNombre)
                .containsExactly(
                        tuple(PrioriteDto.CRITIQUE, 2),
                        tuple(PrioriteDto.HAUTE, 0),
                        tuple(PrioriteDto.NORMALE, 5),
                        tuple(PrioriteDto.BASSE, 1));
        assertThat(dto.getEquipementsEnPanne()).isEqualTo(4);
        assertThat(dto.getTempsMoyenResolutionHeures()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("Classement et évolution : mois en AAAA-MM, écart absent pour le premier mois")
    void classementEtEvolution() {
        DashboardDto dto = mapper.versDto(tableau(4));

        assertThat(dto.getTopEquipements()).singleElement()
                .satisfies(top -> {
                    assertThat(top.getRang()).isEqualTo(1);
                    assertThat(top.getReference()).isEqualTo("PRS-001");
                    assertThat(top.getNombrePannes()).isEqualTo(7);
                });
        assertThat(dto.getEvolutionMensuelle())
                .extracting(DashboardEvolutionMensuelleInnerDto::getMois, DashboardEvolutionMensuelleInnerDto::getNombre,
                        DashboardEvolutionMensuelleInnerDto::getVariation)
                .containsExactly(tuple("2026-09", 10, null), tuple("2026-10", 4, -6));
    }

    @Test
    @DisplayName("Charge : le technicien est exposé avec son rôle, ses interventions terminées et son temps")
    void chargeTechniciens() {
        DashboardDto dto = mapper.versDto(tableau(4));

        assertThat(dto.getChargeTechniciens()).singleElement()
                .satisfies(charge -> {
                    assertThat(charge.getTechnicien().getIdUtilisateur()).isEqualTo(3L);
                    assertThat(charge.getTechnicien().getPrenom()).isEqualTo("Lucas");
                    assertThat(charge.getTechnicien().getNom()).isEqualTo("Bernard");
                    assertThat(charge.getTechnicien().getRole()).isEqualTo(RoleDto.TECHNICIEN);
                    assertThat(charge.getInterventionsMois()).isEqualTo(9);
                    assertThat(charge.getTempsPasseMinMois()).isEqualTo(540);
                });
    }

    @Test
    @DisplayName("Un compteur qui dépasse un entier 32 bits fait échouer la conversion")
    void depassement() {
        assertThatThrownBy(() -> mapper.versDto(tableau(Integer.MAX_VALUE + 1L)))
                .isInstanceOf(ArithmeticException.class);
    }
}
