*** Settings ***
Documentation       Mots-clés communs des parcours de MécaDétect.
...                 Les sélecteurs s'appuient sur ce que voit l'utilisateur (titres, libellés, textes des boutons)
...                 ou sur les attributs stables d'Angular (formcontrolname), jamais sur la mise en page.
Library             Browser    timeout=10s
Library             DateTime


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
${NOM_TECHNICIEN}       Lucas Bernard
# Variable d'environnement MECADETECT_MOT_DE_PASSE si elle existe, sinon le mot de passe de démonstration
${MOT_DE_PASSE}         %{MECADETECT_MOT_DE_PASSE=Demo2026!}


*** Keywords ***
# ------------------------------------------------------------------ Navigateur et connexion
Ouvrir le navigateur
    [Documentation]    Un seul navigateur pour toute la suite (démarrage coûteux).
    New Browser    chromium    headless=${HEADLESS}

Ouvrir la page de connexion
    [Documentation]    Nouveau contexte : stockage vide, aucune session héritée.
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

Se connecter en tant que
    [Documentation]    Session neuve pour un autre rôle, comme sur un autre poste.
    [Arguments]    ${email}
    Ouvrir la page de connexion
    Se connecter    ${email}
    Wait For Condition    Url    not contains    /login

Fermer les sessions
    Close Context    ALL

La page affichée est
    [Documentation]    Vérifie l'adresse et le titre principal de la page.
    [Arguments]    ${chemin}    ${titre}
    Wait For Condition    Url    should end with    ${chemin}
    Get Text    h1 >> nth=0    ==    ${titre}

# ------------------------------------------------------------------ Formulaires Angular Material
Choisir dans la liste
    [Documentation]    Ouvre un mat-select puis clique l'option qui contient le texte.
    ...                Un mat-select sans option refuse de s'ouvrir : quand la liste vient de l'API
    ...                (techniciens habilités), on réessaie jusqu'à ce que les options soient arrivées.
    [Arguments]    ${controle}    ${texte}
    Wait Until Keyword Succeeds    10s    300ms    Ouvrir la liste    ${controle}    ${texte}
    Click    mat-option >> text=${texte}

Ouvrir la liste
    [Arguments]    ${controle}    ${texte}
    ${ouverte}=    Get Element Count    .mat-mdc-select-panel
    IF    ${ouverte} == 0    Click    mat-select[formcontrolname="${controle}"]
    Wait For Elements State    mat-option >> text=${texte}    visible    timeout=1s

# ------------------------------------------------------------------ Interventions
Déclarer une panne
    [Documentation]    Remplit le formulaire de déclaration et renvoie l'identifiant de l'intervention créée.
    [Arguments]    ${equipement}    ${titre}    ${priorite}
    Go To    ${URL}/interventions/declarer
    La page affichée est    /interventions/declarer    Déclarer une panne
    Choisir dans la liste    idEquipement    ${equipement}
    Fill Text    input[formcontrolname="titre"]    ${titre}
    Fill Text    textarea[formcontrolname="description"]
    ...    Panne déclarée par un test de bout en bout : fuite constatée au démarrage du poste.
    Click    mat-button-toggle >> text=${priorite}
    Click    button[type="submit"] >> text=Déclarer la panne
    # Après la déclaration, l'application ouvre la fiche de l'intervention : /interventions/42
    Wait For Condition    Url    matches    /interventions/[0-9]+$
    ${adresse}=    Get Url
    ${id}=    Evaluate    $adresse.rstrip('/').split('/')[-1]
    RETURN    ${id}

Ouvrir l'intervention
    [Arguments]    ${id}
    Go To    ${URL}/interventions/${id}
    Wait For Elements State    .entete h1    visible

Le statut affiché est
    [Arguments]    ${libelle}
    Get Text    .entete app-statut-intervention-badge    *=    ${libelle}