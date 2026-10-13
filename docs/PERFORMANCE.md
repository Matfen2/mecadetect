# Performance des requêtes - MécaDétect

Ce document consigne l'analyse des requêtes critiques sur un volume réaliste (tâches 4.4 et 4.7, cas d'usage UC-DB3 et UC-DB5) : ce qui était lent, pourquoi, ce qui a été changé, et le gain mesuré.

## En bref

| Requête | Avant | Après | Gain |
| --- | --- | --- | --- |
| Liste des interventions, page 1 | 480 ms | 6,5 ms | ÷ 74 |
| Historique d'un équipement | 234 ms | 4,5 ms | ÷ 52 |
| Liste filtrée sur un technicien | 476 ms | 2 ms | ÷ 238 |
| Détail d'une intervention (historique) | 37 ms | < 0,5 ms | — |
| Mes interventions (technicien) | 50 ms | 0,5 ms | ÷ 100 |

Trois causes, trois corrections : des clés étrangères sans index, une requête de recherche écrite de façon à empêcher tout usage d'index, et un tri mélangeant deux sens. Le reporting, lui, n'a pas été indexé : la mesure montre qu'un index ne lui apporte rien.

## Méthode

**Jeu de données.** 500 004 interventions réparties sur 5 ans et 1 915 283 lignes d'historique, générées par `PKG_GENERATION` (tâche 4.1) : 89 % terminées, 10 % annulées, environ 4 000 ouvertes, 20 équipements, 12 techniciens. Le tirage est volontairement déséquilibré : PRS-001 cumule 121 242 interventions, Lucas en a 84 841.

**Mesure.** Le script [`scripts/mesures-performance.sql`](../scripts/mesures-performance.sql) reproduit les requêtes envoyées par Hibernate, avec des paramètres liés (binds) comme en JDBC. Pour chacune, il affiche :

- le plan d'exécution estimé (`EXPLAIN PLAN`, `DBMS_XPLAN`) ;
- le temps moyen sur 20 exécutions, après une exécution de chauffe. Les données sont alors en mémoire : on mesure le travail d'Oracle, pas la lecture disque.

**Environnement.** Oracle Database 23ai Free dans Docker, sur un poste de développement Windows. La résolution de la mesure est de 0,5 ms (`DBMS_UTILITY.GET_TIME` donne des centièmes de seconde, divisés par 20 exécutions). Un « 0,0 ms » signifie « moins de 0,5 ms ».

**Quatre campagnes**, chacune conservée telle quelle dans `docs/` :

| Campagne | Fichier | Changement |
| --- | --- | --- |
| Avant | `mesures-avant.txt` | Aucun : clés primaires et contraintes d'unicité seulement |
| SQL dynamique | `mesures-sql-dynamique.txt` | Recherche réécrite, sans nouvel index |
| Index V5 | `mesures-apres.txt` | Migration V5 : cinq index |
| Final | `mesures-final.txt` | Migration V6 et sens du tri corrigé |

## Résultats détaillés

Temps moyen par exécution, en millisecondes. Pour les listes, le temps inclut la page de 20 lignes et la requête de comptage qui l'accompagne.

| Requête | Avant | SQL dynamique | Index V5 | Final |
| --- | --- | --- | --- | --- |
| Q1 Détail : historique d'une intervention | 37 | 184,5 \* | < 0,5 | < 0,5 |
| Q2a Liste sans filtre | 480 | 307 | 151 | **6,5** |
| Q2b Liste filtrée sur un statut | 122 | 87 | 2 | **2,5** |
| Q2c Historique de PRS-001 | 234 | 169,5 | 69 | **4,5** |
| Q2d Liste filtrée sur Lucas | 476,5 | 146 | 80,5 | **2** |
| Q3 Mes interventions | 50,5 | 54 | 0,5 | **0,5** |
| Q4 RG-04, technicien avec une intervention en cours | < 0,5 | < 0,5 | < 0,5 | < 0,5 |
| Q4b RG-04, technicien sans intervention en cours | — | 38 | < 0,5 | **< 0,5** |
| Q5 RG-05, intervention ouverte sur l'équipement | < 0,5 | < 0,5 | < 0,5 | < 0,5 |
| Q6 Reporting : top 10 des équipements sur 12 mois | 52,5 | 56 | 56 | 52,5 |

\* Q1 n'a pas changé entre les deux premières campagnes : cette valeur est un écart de mesure (activité de la machine pendant l'exécution), pas une régression. Elle est laissée telle quelle.

Q4 et Q5 étaient déjà instantanées « avant » par chance : la première ligne lue répondait à la question, et le parcours s'arrêtait là. Q4b mesure le pire cas, quand il faut tout examiner pour répondre « non » : 38 ms sans index.

## Cause 1 - Des clés étrangères sans index

Sans index, chercher les 4 lignes d'historique d'une intervention oblige Oracle à lire les 1,9 million de lignes de la table.

```
Avant                                         Après (V5)
TABLE ACCESS FULL | HISTORIQUE_STATUT         INDEX RANGE SCAN | IDX_HISTORIQUE_INTERVENTION
coût 3 582                                    coût 3
```

Oracle n'indexe pas automatiquement les clés étrangères. Ici, aucune ne l'était : `historique_statut.id_intervention`, `intervention.id_equipement` et `intervention.id_technicien`. En plus de la lecture, une clé étrangère sans index fait verrouiller toute la table enfant quand on supprime une ligne parente.

**Correction (V5)** : `historique_statut (id_intervention, date_changement)`, `intervention (id_technicien, statut)`, `intervention (id_equipement, …)`.

## Cause 2 - Des filtres optionnels qui empêchent tout index

La recherche du Sprint 3 était une seule requête JPQL pour toutes les combinaisons de filtres :

```sql
WHERE (:statut IS NULL OR i.statut = :statut)
  AND (:idTechnicien IS NULL OR t.id = :idTechnicien)
  ...
```

Oracle calcule un plan valable pour toutes les valeurs possibles des paramètres, y compris quand ils sont tous renseignés ou tous vides. Il ne peut donc s'appuyer sur aucun index : le plan « avant » parcourt toute la table, quels que soient les filtres. **Créer des index n'aurait rien changé.**

**Correction** (`InterventionRechercheRepositoryImpl`) : la requête est construite avec l'API Criteria de JPA, et seuls les filtres demandés apparaissent dans le SQL. Chaque combinaison a sa propre requête, et donc son propre plan. Le comptage n'a plus aucune jointure : il ne lit que la table `intervention`, souvent son index seul.

Effet mesuré sans nouvel index : de 476 à 146 ms pour le filtre technicien. Le vrai gain viendra des index, que cette réécriture rend enfin utilisables.

## Cause 3 - Un tri que l'index ne pouvait pas fournir

Après la V5, les comptages utilisaient bien les index, mais les pages de la liste et de l'historique d'un équipement parcouraient encore toute la table : 151 et 69 ms.

Le tri était `date_demande DESC, id_intervention ASC`, deux sens différents. Un index lu à l'endroit ou à l'envers ne peut pas fournir cet ordre : Oracle triait donc les 500 000 lignes pour en afficher 20.

**Correction** :

1. **Java** (`PaginationIntervention`) : l'identifiant départage les ex aequo dans le même sens que le tri demandé, soit `date_demande DESC, id_intervention DESC`. L'affichage ne change pas pour l'utilisateur.
2. **Base** (V6) : l'identifiant rejoint les index `(date_demande, id_intervention)` et `(id_equipement, date_demande, id_intervention)`.

Oracle lit maintenant l'index à l'envers et s'arrête dès la 20e ligne :

```
Avant (V5)                                    Final (V6)
WINDOW SORT PUSHED RANK    500K lignes        WINDOW NOSORT STOPKEY        20 lignes
  TABLE ACCESS FULL        INTERVENTION         INDEX FULL SCAN DESCENDING  IDX_INTERVENTION_DATE_DEMANDE
coût 20 085                                   coût 65
```

Le reste des 6,5 ms de la liste sans filtre vient du comptage des 500 000 lignes, fait sur l'index de la clé primaire (`INDEX FAST FULL SCAN`) : c'est le prix d'afficher « 1 – 20 sur 500 004 ».

## Le contre-exemple : le reporting

Le top 10 des équipements porte sur 12 mois, soit environ 20 % de la table. Avec les index, Oracle change de plan et combine deux index (`BITMAP MINUS`), avec un coût estimé divisé par 9. **Le temps réel ne bouge pas** : 52,5 ms avant comme après.

Quand une requête lit une grande part de la table, la parcourir entièrement est aussi efficace que passer par un index. Aucun index n'a donc été ajouté pour le reporting. Le coût estimé par l'optimiseur n'est qu'une prévision : seule la mesure tranche.

## Compromis assumés

- **Le coût en écriture.** La table `intervention` compte cinq index de plus, et chaque déclaration ou transition les met à jour. Dans une GMAO, on lit bien plus qu'on n'écrit : quelques déclarations par heure, contre des centaines de consultations.
- **Le filtre technicien de l'admin (Q2d) n'a pas d'index dédié.** Oracle lit l'index des dates à l'envers et garde les lignes du technicien demandé. C'est très rapide pour Lucas, qui a 17 % des interventions : 2 ms. Pour un technicien beaucoup moins présent, Oracle lirait davantage de lignes avant d'en trouver 20. Un index `(id_technicien, date_demande, id_intervention)` réglerait ce cas, au prix d'un sixième index pour un filtre ponctuel. Il pourra être ajouté si la mesure en production le justifie.
- **Le comptage total** de la liste sans filtre reste proportionnel au volume. Au-delà de quelques millions de lignes, on pourrait afficher « plus de 10 000 » plutôt qu'un total exact.

## Problème N+1 (tâche 4.7)

Le test `InterventionRequetesSqlTest` compte les requêtes SQL envoyées pour afficher 20 interventions, chacune avec un équipement et un technicien différents :

| Approche | Requêtes SQL |
| --- | --- |
| Charger les entités, puis lire l'équipement et le technicien de chacune | 41 (1 + 20 + 20) |
| Recherche projetée en DTO (`InterventionResume`) | 2 (la page et son comptage) |

La projection récupère en une seule requête, avec jointures, exactement les colonnes affichées. Aucune entité n'est chargée, et aucun chargement différé n'est déclenché.

## Archivage des interventions closes (UC-DB4, tâche 4.5)

`PKG_MAINTENANCE.archiver_interventions` déplace vers `intervention_archive` et `historique_statut_archive` les interventions terminées avant une date limite, et les annulées dont la demande précède cette date. Le traitement se fait par lots de 10 000 : `BULK COLLECT` des identifiants, puis quatre `FORALL` (copie des interventions, copie de l'historique, suppression de l'historique, suppression des interventions), avec un commit par lot.

Essai sur le jeu de 500 004 interventions, avec une date limite à 3 ans (script `archivage-essai.sql`, exécuté à blanc : `p_commit => FALSE` puis `ROLLBACK`) :

| | Avant | Archivé | Restant en ligne |
| --- | --- | --- | --- |
| Interventions | 500 004 | 199 155 | 300 849 |
| Lignes d'historique | 1 915 283 | 766 748 | 1 148 535 |

- **Durée : 79,7 s** pour 965 903 lignes déplacées, soit environ 12 000 lignes par seconde, ou 0,4 ms par intervention avec son historique. Cela représente 20 lots d'environ 4 s.
- **Chaque ligne coûte deux écritures** (une insertion dans l'archive et une suppression), plus la mise à jour des index. Côté `intervention`, ce sont 7 index : la clé primaire, l'unicité du numéro et les 5 index de V5/V6. C'est la contrepartie en écriture des index qui accélèrent les lectures.
- **Le journal fonctionne comme prévu.** La ligne SUCCES (199 155 / 766 748) est restée dans `journal_archivage` malgré le `ROLLBACK` final, car elle est écrite en transaction autonome.
- **Une piste d'optimisation, non mesurée.** Chaque lot relit la table depuis le début pour trouver les 10 000 interventions suivantes. Un curseur unique parcouru par `FETCH … LIMIT` éviterait ces relectures. Pour un traitement d'administration lancé ponctuellement, 80 s restent acceptables, et l'optimisation ne vaut d'être faite que si la mesure en production le justifie.

Une intervention archivée n'est plus consultable depuis l'application : sa fiche répond 404. C'est pourquoi l'API refuse une date limite de moins d'un an : la dernière année reste toujours en ligne, pour la planification, l'historique des équipements et le reporting.

## Reproduire les mesures

Depuis la racine du dépôt, la base générée étant en place (`EXEC pkg_generation.generer_interventions;`) :

```powershell
docker cp scripts\mesures-performance.sql mecadetect-oracle:/tmp/mesures-performance.sql
docker exec -it mecadetect-oracle bash -c 'sqlplus -s $APP_USER/$APP_USER_PASSWORD@FREEPDB1 @/tmp/mesures-performance.sql nom-de-la-campagne'
docker cp mecadetect-oracle:/tmp/mesures-nom-de-la-campagne.txt docs\
```

Essai d'archivage à blanc (les données sont conservées) :

```powershell
docker cp scripts\archivage-essai.sql mecadetect-oracle:/tmp/
docker exec -it mecadetect-oracle bash -c 'sqlplus -s $APP_USER/$APP_USER_PASSWORD@FREEPDB1 @/tmp/archivage-essai.sql'
```

## Prochaine étape

Les scénarios de charge Gatling (tâche 4.8) mesureront les mêmes parcours à travers l'API complète, avec plusieurs utilisateurs simultanés. Les percentiles des temps de réponse compléteront ce document.