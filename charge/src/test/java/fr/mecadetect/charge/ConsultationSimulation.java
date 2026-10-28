package fr.mecadetect.charge;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Tâche 4.8 : une journée d'atelier en accéléré, à travers l'API complète (sécurité, JPA, JSON).
 *
 * Trois profils d'utilisateurs connectés en même temps, qui enchaînent les écrans avec un temps
 * de réflexion de 1 à 3 s entre deux pages :
 *  - le responsable de maintenance parcourt la liste, la filtre (statut, technicien, période),
 *    pagine, ouvre des fiches et consulte la charge des techniciens ;
 *  - le technicien, sur sa tablette, consulte « Mes interventions » et ouvre l'une d'elles ;
 *  - le demandeur consulte la liste des équipements, puis la fiche et l'historique de l'un d'eux.
 *
 * Scénario en lecture seule : deux campagnes successives portent exactement sur les mêmes données.
 *
 * Paramètres (-Dnom=valeur) : url, motDePasse, responsables, techniciens, demandeurs, duree, montee.
 */
public class ConsultationSimulation extends Simulation {

    private static final String URL = System.getProperty("url", "http://localhost:8080/api/v1");
    /** Mot de passe des comptes de démonstration (profil demo, documenté dans le README). */
    private static final String MOT_DE_PASSE = System.getProperty("motDePasse", "Demo2026!");

    private static final int RESPONSABLES = Integer.getInteger("responsables", 5);
    private static final int TECHNICIENS = Integer.getInteger("techniciens", 20);
    private static final int DEMANDEURS = Integer.getInteger("demandeurs", 10);
    private static final Duration DUREE = Duration.ofSeconds(Integer.getInteger("duree", 120));
    private static final Duration MONTEE = Duration.ofSeconds(Integer.getInteger("montee", 15));

    private static final Duration REFLEXION_MIN = Duration.ofSeconds(1);
    private static final Duration REFLEXION_MAX = Duration.ofSeconds(3);

    private final HttpProtocolBuilder protocole = http
            .baseUrl(URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .userAgentHeader("Gatling - MecaDetect");

    // ------------------------------------------------------------------ briques communes

    private static ChainBuilder connexion(String email) {
        return exec(http("Connexion")
                .post("/auth/login")
                .body(StringBody("{\"email\": \"%s\", \"motDePasse\": \"%s\"}".formatted(email, MOT_DE_PASSE)))
                .check(status().is(200))
                .check(jsonPath("$.token").saveAs("jeton"))
                .check(jsonPath("$.utilisateur.idUtilisateur").saveAs("idUtilisateur")))
                // Sans jeton, inutile de continuer : l'utilisateur virtuel s'arrête au lieu de boucler en erreur
                .exitHereIfFailed();
    }

    /** GET authentifié qui doit répondre 200. Le nom regroupe les requêtes dans le rapport. */
    private static HttpRequestActionBuilder lire(String nom, String chemin) {
        return http(nom)
                .get(chemin)
                .header("Authorization", "Bearer #{jeton}")
                .check(status().is(200));
    }

    private static ChainBuilder reflexion() {
        return pause(REFLEXION_MIN, REFLEXION_MAX);
    }

    /** Un mois au hasard sur les 5 ans d'historique. */
    private static ChainBuilder moisAuHasard() {
        return exec(session -> {
            LocalDate debut = LocalDate.now().withDayOfMonth(1)
                    .minusMonths(ThreadLocalRandom.current().nextInt(1, 60));
            return session.set("depuis", debut.toString())
                    .set("jusqua", debut.plusMonths(1).minusDays(1).toString());
        });
    }

    private static ChainBuilder pageAuHasard() {
        return exec(session -> session.set("page", ThreadLocalRandom.current().nextInt(1, 50)));
    }

    // ------------------------------------------------------------------ scénarios

    private final ScenarioBuilder responsable = scenario("Responsable de maintenance")
            .exec(connexion("admin@mecadetect.fr"))
            .during(DUREE).on(
                    exec(lire("Interventions - première page", "/interventions?page=0&size=20&sort=dateDemande,desc")
                            .check(jsonPath("$.contenu[*].idIntervention").findRandom().saveAs("idIntervention")))
                    .exec(reflexion())
                    .exec(lire("Intervention - détail", "/interventions/#{idIntervention}"))
                    .exec(reflexion())
                    .exec(lire("Interventions - à planifier", "/interventions?statut=DEMANDEE&size=20"))
                    .exec(reflexion())
                    .exec(lire("Techniciens - charge", "/techniciens")
                            .check(jsonPath("$[*].idUtilisateur").findRandom().saveAs("idTechnicien")))
                    .exec(reflexion())
                    .exec(lire("Interventions - par technicien", "/interventions?idTechnicien=#{idTechnicien}&size=20"))
                    .exec(reflexion())
                    .exec(moisAuHasard())
                    .exec(lire("Interventions - sur un mois", "/interventions?depuis=#{depuis}&jusqua=#{jusqua}&size=20"))
                    .exec(reflexion())
                    .exec(pageAuHasard())
                    .exec(lire("Interventions - page suivante", "/interventions?page=#{page}&size=20"))
                    .exec(reflexion()));

    private final ScenarioBuilder technicien = scenario("Technicien")
            .exec(connexion("technicien@mecadetect.fr"))
            .during(DUREE).on(
                    exec(lire("Mes interventions", "/techniciens/#{idUtilisateur}/interventions?size=20")
                            .check(jsonPath("$.contenu[*].idIntervention").findRandom().optional()
                                    .saveAs("idMonIntervention")))
                    .exec(reflexion())
                    .doIf(session -> session.contains("idMonIntervention")).then(
                            exec(lire("Intervention - détail", "/interventions/#{idMonIntervention}"))
                            .exec(reflexion())));

    private final ScenarioBuilder demandeur = scenario("Demandeur")
            .exec(connexion("demandeur@mecadetect.fr"))
            .during(DUREE).on(
                    exec(lire("Équipements - liste", "/equipements?size=20&sort=reference,asc")
                            .check(jsonPath("$.contenu[*].idEquipement").findRandom().saveAs("idEquipement")))
                    .exec(reflexion())
                    // La fiche charge l'équipement et son historique en même temps
                    .exec(lire("Équipement - fiche", "/equipements/#{idEquipement}"))
                    .exec(lire("Équipement - historique", "/equipements/#{idEquipement}/interventions?size=10"))
                    .exec(reflexion()));

    {
        setUp(
                responsable.injectOpen(rampUsers(RESPONSABLES).during(MONTEE)),
                technicien.injectOpen(rampUsers(TECHNICIENS).during(MONTEE)),
                demandeur.injectOpen(rampUsers(DEMANDEURS).during(MONTEE)))
                .protocols(protocole)
                .assertions(
                        // Objectifs de service : aucune erreur, et des écrans qui répondent vite
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile3().lt(300),
                        details("Interventions - première page").responseTime().percentile3().lt(200));
    }
}