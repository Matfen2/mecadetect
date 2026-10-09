package fr.mecadetect.referentiel;

import fr.mecadetect.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ReferentielApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private TypeEquipementRepository typeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        zoneRepository.saveAndFlush(new Zone("B2", "Atelier B"));
        zoneRepository.saveAndFlush(new Zone("A1", "Atelier A"));
        typeRepository.saveAndFlush(new TypeEquipement("Tour CN"));
        typeRepository.saveAndFlush(new TypeEquipement("Compresseur"));
    }

    @Test
    @DisplayName("GET /zones renvoie les zones triées par code")
    void zones() throws Exception {
        mockMvc.perform(get("/api/v1/zones").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DEMANDEUR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("A1"))
                .andExpect(jsonPath("$[1].code").value("B2"));
    }

    @Test
    @DisplayName("GET /types-equipement renvoie les types triés par libellé")
    void typesEquipement() throws Exception {
        mockMvc.perform(get("/api/v1/types-equipement").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DEMANDEUR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libelle").value("Compresseur"))
                .andExpect(jsonPath("$[1].libelle").value("Tour CN"));
    }

    @Test
    @DisplayName("Le référentiel exige une authentification : 401")
    void sansAuthentification() throws Exception {
        mockMvc.perform(get("/api/v1/zones")).andExpect(status().isUnauthorized());
    }
}