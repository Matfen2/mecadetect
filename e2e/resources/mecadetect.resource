*** Settings ***
Documentation       Mots-clés communs des parcours de MécaDétect.
...                 Les sélecteurs s'appuient sur ce que voit l'utilisateur (titres, libellés)
...                 ou sur les attributs stables d'Angular (formcontrolname), jamais sur la mise en page.
Library             Browser    timeout=10s


*** Variables ***
# Application visée : le serveur de développement par défaut.
# Autre cible : robot -v URL:https://localhost ...
${URL}                  http://localhost:4200
# Navigateur visible : robot -v HEADLESS:False ...
${HEADLESS}             True

# Comptes de démonstration (profil demo)
${EMAIL_ADMIN}          admin@mecadetect.fr
${EMAIL_TECHNICIEN}     technicien@mecadetect.fr
${EMAIL_DEMANDEUR}      demandeur@mecadetect.fr
# Variable d'environnement MECADETECT_MOT_DE_PASSE si elle existe, sinon le mot de passe de démonstration
${MOT_DE_PASSE}         %{MECADETECT_MOT_DE_PASSE=Demo2026!}


*** Keywords ***
Ouvrir le navigateur
    [Documentation]    Un seul navigateur pour toute la suite (démarrage coûteux).
    New Browser    chromium    headless=${HEADLESS}

Ouvrir la page de connexion
    [Documentation]    Nouveau contexte à chaque test : stockage vide, aucune session héritée.
    ...                ignoreHTTPSErrors : le certificat local de Caddy (DOMAINE=localhost) n'est pas reconnu.
    New Context    locale=fr-FR    ignoreHTTPSErrors=True
    New Page    ${URL}/login
    Get Title    ==    Connexion · MécaDétect

Se connecter
    [Arguments]    ${email}    ${mot_de_passe}=${MOT_DE_PASSE}
    Fill Text    input[formcontrolname="email"]    ${email}
    # Fill Secret reçoit le nom de la variable ($mot_de_passe) et non sa valeur :
    # le mot de passe n'apparaît jamais dans le journal de Robot Framework
    Fill Secret    input[formcontrolname="motDePasse"]    $mot_de_passe
    Click    button[type="submit"]

La page affichée est
    [Documentation]    Vérifie l'adresse et le titre principal de la page.
    [Arguments]    ${chemin}    ${titre}
    Wait For Condition    Url    should end with    ${chemin}
    Get Text    h1 >> nth=0    ==    ${titre}