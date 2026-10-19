*** Settings ***
Documentation       Tâche 6.2 : les parcours critiques d'une intervention, à travers les trois rôles.
...                 Chaque test crée sa propre intervention (titre horodaté) : il ne dépend
...                 ni des données existantes ni de l'ordre des tests.
...
...                 Prérequis : le technicien de démonstration n'a aucune intervention en cours
...                 (RG-04 : une seule à la fois). C'est le cas sur une base de démonstration neuve.
Resource            ../resources/mecadetect.resource

Suite Setup         Ouvrir le navigateur
Suite Teardown      Close Browser
Test Teardown       Fermer les sessions


*** Variables ***
${EQUIPEMENT}       PRS-001


*** Test Cases ***
Une panne est déclarée, planifiée, réalisée puis clôturée
    [Documentation]    Le parcours de démonstration du README, de bout en bout.
    ${titre}=    Titre unique    Fuite d'huile
    # 1. Le demandeur déclare la panne : l'intervention est créée au statut « Demandée »
    Se connecter en tant que    ${EMAIL_DEMANDEUR}
    ${id}=    Déclarer une panne    ${EQUIPEMENT}    ${titre}    Haute
    Le statut affiché est    Demandée

    # 2. Le responsable la planifie : seuls les techniciens habilités sont proposés
    Se connecter en tant que    ${EMAIL_ADMIN}
    Ouvrir l'intervention    ${id}
    Click    button >> text=Planifier
    Choisir dans la liste    idTechnicien    ${NOM_TECHNICIEN}
    # La date est préremplie avec la prochaine heure pleine
    Click    form.panneau button[type="submit"]
    Le statut affiché est    Planifiée

    # 3. Le technicien la démarre puis la clôture, depuis la fiche de l'intervention.
    #    (« Mes interventions » est paginée : avec un historique généré, la nouvelle intervention
    #    peut se trouver au-delà de la première page. La fiche, elle, est toujours accessible.)
    Se connecter en tant que    ${EMAIL_TECHNICIEN}
    La page affichée est    /mes-interventions    Mes interventions
    Ouvrir l'intervention    ${id}
    Click    button >> text=Démarrer l'intervention
    Le statut affiché est    En cours

    Click    .actions button >> text=Clôturer
    Fill Text    textarea[formcontrolname="compteRendu"]
    ...    Joint du vérin remplacé, essai sous pression concluant.
    Fill Text    input[formcontrolname="tempsPasseMin"]    45
    Click    form.panneau button[type="submit"]
    Le statut affiché est    Terminée

    # 4. Le responsable retrouve l'intervention terminée, avec son compte rendu et tout son historique
    Se connecter en tant que    ${EMAIL_ADMIN}
    Ouvrir l'intervention    ${id}
    Le statut affiché est    Terminée
    Get Text    .bloc >> text=Joint du vérin remplacé    *=    essai sous pression
    # Demandée, Planifiée, En cours, Terminée : une ligne par changement de statut
    Get Element Count    ol.historique > li    ==    4

Le responsable annule une demande avec un motif
    ${titre}=    Titre unique    Bruit anormal
    Se connecter en tant que    ${EMAIL_DEMANDEUR}
    ${id}=    Déclarer une panne    ${EQUIPEMENT}    ${titre}    Normale

    Se connecter en tant que    ${EMAIL_ADMIN}
    Ouvrir l'intervention    ${id}
    Click    button >> text=Annuler l'intervention
    Fill Text    textarea[formcontrolname="motif"]    Doublon d'une demande déjà en cours.
    Click    button >> text=Confirmer l'annulation
    Le statut affiché est    Annulée
    Get Text    .bloc >> text=Doublon d'une demande    *=    Doublon

Le demandeur ne voit ni planification ni annulation
    [Documentation]    Les boutons d'action dépendent du rôle : le demandeur ne fait que suivre sa demande.
    ${titre}=    Titre unique    Défaut capteur
    Se connecter en tant que    ${EMAIL_DEMANDEUR}
    ${id}=    Déclarer une panne    ${EQUIPEMENT}    ${titre}    Basse
    Get Element Count    button >> text=Planifier    ==    0
    Get Element Count    button >> text=Annuler l'intervention    ==    0


*** Keywords ***
Titre unique
    [Documentation]    Titre horodaté : chaque exécution crée des interventions qu'on retrouve sans ambiguïté.
    [Arguments]    ${debut}
    ${horodatage}=    Get Current Date    result_format=%d/%m %H:%M:%S
    RETURN    ${debut} (test E2E ${horodatage})