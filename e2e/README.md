# Tests de bout en bout

Parcours de MécaDétect joués dans un vrai navigateur, avec [Robot Framework](https://robotframework.org)
et la bibliothèque [Browser](https://robotframework-browser.org) (Playwright).

## Installation (une fois)

Prérequis : Python 3.10 ou plus récent, Node.js 24.

```powershell
python -m venv e2e/.venv
e2e/.venv/Scripts/Activate.ps1        # Linux, macOS : source e2e/.venv/bin/activate
pip install -r e2e/requirements.txt
rfbrowser init chromium
```

## Lancer les tests

L'API (profil `demo`) et le front doivent tourner (voir le README principal).

```powershell
robot --outputdir e2e/resultats e2e/tests
```

- `-v HEADLESS:False` : voir le navigateur pendant les tests.
- `-v URL:https://localhost` : viser le déploiement Docker Compose local.

Le rapport est dans `e2e/resultats/report.html`, avec une capture d'écran de chaque échec.