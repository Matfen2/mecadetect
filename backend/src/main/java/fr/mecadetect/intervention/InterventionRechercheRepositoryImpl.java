package fr.mecadetect.intervention;

import fr.mecadetect.equipement.Equipement;
import fr.mecadetect.utilisateur.Utilisateur;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/// Tâche 4.4 : la requête ne contient que les filtres réellement demandés.
///
/// L'ancienne version JPQL écrivait chaque filtre sous la forme (:p is null or colonne = :p).
/// Le plan d'exécution est calculé une fois pour toutes les combinaisons de paramètres :
/// Oracle ne peut alors utiliser aucun index et parcourt toute la table (mesuré : 480 ms
/// pour la première page sur 500 000 interventions). Ici, un filtre absent n'apparaît pas
/// dans le SQL : chaque combinaison a sa propre requête, et peut utiliser l'index adapté.
///
/// La projection reste une seule requête (pas de N+1) ; le comptage ne fait aucune jointure.
///
/// Tâche 4.8 : Hibernate limite la page par « OFFSET :n ROWS FETCH FIRST :m ROWS ONLY » avec des
/// valeurs liées. En calculant le plan, Oracle ignore alors qu'on ne veut que 20 lignes : il optimise
/// pour toutes les renvoyer (parcours complet de la table et tri). L'indication FIRST_ROWS(n) lui
/// rend cette information : il lit l'index déjà trié et s'arrête après la page (WINDOW NOSORT STOPKEY).
/// Mesuré sur 500 000 interventions : 141,5 ms → moins de 0,5 ms (scripts/plan-limite-lignes.sql).
class InterventionRechercheRepositoryImpl implements InterventionRechercheRepository {

    /// Champ projeté et filtré par période.
    private static final String DATE_DEMANDE = "dateDemande";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<InterventionResume> rechercher(StatutIntervention statut, Priorite priorite, Long idTechnicien,
                                               Long idEquipement, LocalDateTime depuis, LocalDateTime avant,
                                               Pageable pageable) {
        var filtres = new Filtres(statut, priorite, idTechnicien, idEquipement, depuis, avant);
        var cb = entityManager.getCriteriaBuilder();

        var requete = cb.createQuery(InterventionResume.class);
        Root<Intervention> i = requete.from(Intervention.class);
        var e = i.<Intervention, Equipement>join("equipement");
        var t = i.<Intervention, Utilisateur>join("technicien", JoinType.LEFT);
        requete.select(cb.construct(InterventionResume.class,
                        i.get("id"), i.get("numero"), i.get("titre"), i.get("priorite"), i.get("statut"),
                        i.get(DATE_DEMANDE), i.get("datePlanifiee"),
                        e.get("reference"), e.get("nom"),
                        t.get("id"), t.get("prenom"), t.get("nom")))
                .where(filtres.predicats(cb, i))
                .orderBy(tri(cb, i, pageable));

        List<InterventionResume> contenu = entityManager.createQuery(requete)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .setHint(HibernateHints.HINT_QUERY_DATABASE, indicationPremieresLignes(pageable))
                .getResultList();

        return new PageImpl<>(contenu, pageable, compter(cb, filtres));
    }

    /// Indication Oracle pour la page demandée : seule la taille de page y figure, pour que le texte SQL
    /// (et donc le plan en cache) reste le même d'une page à l'autre.
    static String indicationPremieresLignes(Pageable pageable) {
        return "FIRST_ROWS(" + pageable.getPageSize() + ")";
    }

    /// Comptage sur la seule table INTERVENTION : les filtres portent sur ses propres colonnes.
    private long compter(CriteriaBuilder cb, Filtres filtres) {
        var requete = cb.createQuery(Long.class);
        Root<Intervention> i = requete.from(Intervention.class);
        requete.select(cb.count(i)).where(filtres.predicats(cb, i));
        return entityManager.createQuery(requete).getSingleResult();
    }

    private static List<Order> tri(CriteriaBuilder cb, Root<Intervention> i, Pageable pageable) {
        var ordres = new ArrayList<Order>();
        pageable.getSort().forEach(ordre -> ordres.add(ordre.isAscending()
                ? cb.asc(i.get(ordre.getProperty()))
                : cb.desc(i.get(ordre.getProperty()))));
        return ordres;
    }

    /// Les filtres demandés ; seuls ceux qui ont une valeur deviennent des prédicats SQL.
    private record Filtres(StatutIntervention statut, Priorite priorite, Long idTechnicien,
                           Long idEquipement, LocalDateTime depuis, LocalDateTime avant) {

        Predicate[] predicats(CriteriaBuilder cb, Root<Intervention> i) {
            var predicats = new ArrayList<Predicate>();
            if (statut != null) {
                predicats.add(cb.equal(i.get("statut"), statut));
            }
            if (priorite != null) {
                predicats.add(cb.equal(i.get("priorite"), priorite));
            }
            // Accès à l'identifiant d'une association : Hibernate lit directement la clé étrangère, sans jointure
            if (idTechnicien != null) {
                predicats.add(cb.equal(i.get("technicien").get("id"), idTechnicien));
            }
            if (idEquipement != null) {
                predicats.add(cb.equal(i.get("equipement").get("id"), idEquipement));
            }
            if (depuis != null) {
                predicats.add(cb.greaterThanOrEqualTo(i.get(DATE_DEMANDE), depuis));
            }
            if (avant != null) {
                predicats.add(cb.lessThan(i.get(DATE_DEMANDE), avant));
            }
            return predicats.toArray(Predicate[]::new);
        }
    }
}