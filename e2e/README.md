# Tests de bout en bout

Parcours de MécaDétect joués dans un vrai navigateur, avec [Robot Framework](https://robotframework.org)
et la bibliothèque [Browser](https://robotframework-browser.org) (Playwright).

| Suite | Ce qui est vérifié |
| --- | --- |
| `connexion.robot` | Chaque rôle se connecte et arrive sur sa page d'accueil ; un mauvais mot de passe est refusé |
| `parcours_intervention.robot` | Une panne est déclarée (demandeur), planifiée (responsable), démarrée puis clôturée (technicien) ; une demande est annulée avec un motif ; le demandeur n'a pas accès aux actions du responsable |

Chaque test crée ses propres interventions (titre horodaté) : il ne dépend ni des données existantes
ni de l'ordre des tests.

## Dans la CI

Le job « Tests de bout en bout » de `.github/workflows/ci.yml` tourne sur chaque pull request vers `main`
et après chaque merge sur `main` :

1. les images de l'API et du front sont construites avec les Dockerfiles de production ;
2. `compose.prod.yaml` démarre la pile complète (Oracle, API, front, Caddy) avec une base neuve,
   le profil `demo` et des secrets tirés au hasard ;
3. les parcours sont joués sur `https://localhost` ;
4. le rapport est publié comme artefact du run (`rapport-e2e`), avec une capture de chaque échec.

Le déploiement en production attend ce job : une version qui casse un parcours ne part pas.

## En local

### Installation (une fois)

Prérequis : Python 3.13, Node.js 24.

```powershell
py -3.13 -m venv e2e/.venv
e2e/.venv/Scripts/Activate.ps1        # Linux, macOS : source e2e/.venv/bin/activate
pip install -r e2e/requirements.txt
rfbrowser init chromium
```

### Lancer les tests

L'API (profil `demo`) et le front doivent tourner (voir le README principal).

```powershell
robot --outputdir e2e/resultats e2e/tests
```

- `-v HEADLESS:False` : voir le navigateur pendant les tests.
- `-v URL:https://localhost` : viser la pile Docker Compose locale, comme la CI.

Le rapport est dans `e2e/resultats/report.html`, avec une capture d'écran de chaque échec.

**Prérequis du parcours complet** : le technicien de démonstration ne doit avoir aucune intervention
en cours (règle RG-04 : une seule à la fois). C'est le cas sur une base neuve ; sinon, clôturer
l'intervention en cours avant de relancer.