*** Settings ***
Documentation       Tâche 6.1 : l'application s'ouvre et chaque rôle se connecte.
...                 Après connexion, chacun arrive sur sa page d'accueil.
Resource            ../resources/mecadetect.resource

Suite Setup         Ouvrir le navigateur
Suite Teardown      Close Browser
Test Setup          Ouvrir la page de connexion
Test Teardown       Close Context


*** Test Cases ***
Le responsable arrive sur le tableau de bord
    Se connecter    ${EMAIL_ADMIN}
    La page affichée est    /dashboard    Tableau de bord

Le technicien arrive sur ses interventions
    Se connecter    ${EMAIL_TECHNICIEN}
    La page affichée est    /mes-interventions    Mes interventions

Le demandeur arrive sur la liste des interventions
    Se connecter    ${EMAIL_DEMANDEUR}
    La page affichée est    /interventions    Interventions

Un mauvais mot de passe est refusé
    Se connecter    ${EMAIL_ADMIN}    mauvais-mot-de-passe
    Get Element States    [role="alert"]    contains    visible
    Get Url    *=    /login
