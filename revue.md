PR ouverte uniquement pour la revue de code : elle montre tout le projet depuis le premier commit. **Elle ne sera jamais fusionnée.**

Merci Jason pour ton temps ! Tous les commentaires sont bienvenus, même les plus critiques.

## Où regarder en priorité

| Sujet | Fichiers |
| --- | --- |
| Cycle de vie (sealed interface, pattern matching) | `backend/src/main/java/fr/mecadetect/intervention/Intervention.java`, `InterventionService.java` |
| Recherche dynamique et performance | `InterventionRechercheRepositoryImpl.java`, `PaginationIntervention.java` |
| PL/SQL | `backend/src/main/resources/db/migration/V4__generation_donnees.sql`, `V7__archivage_interventions.sql` |
| Reporting SQL analytique | `reporting/ReportingRepository.java` |
| Sécurité | `securite/SecurityConfig.java` |
| Front Angular | `frontend/src/app/features/` |
| CI/CD et déploiement | `.github/workflows/ci.yml`, `compose.prod.yaml`, `deploiement/` |
| Tests de bout en bout | `e2e/` |

Démo : https://mecadetect.duckdns.org (comptes de test dans le README)