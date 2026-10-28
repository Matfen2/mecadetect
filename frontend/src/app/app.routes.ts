import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { authGuard, roleGuard, visiteurGuard } from './core/auth/auth.guards';
import { SessionService } from './core/auth/session.service';


export const routes: Routes = [
  {
    path: 'login',
    title: 'Connexion · MécaDétect',
    canActivate: [visiteurGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    // Toutes les pages connectées partagent le shell (barre de navigation)
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      {
        path: 'equipements',
        title: 'Équipements · MécaDétect',
        loadComponent: () =>
          import('./features/equipements/list-equipements').then((m) => m.ListeEquipements),
      },
      {
        // Déclarées avant :id, sinon « nouveau » et « import » seraient lus comme des identifiants
        path: 'equipements/nouveau',
        title: 'Nouvel équipement · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/equipements/formulaire-equipement').then((m) => m.FormulaireEquipement),
      },
      {
        path: 'equipements/import',
        title: 'Importer le parc · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/equipements/import-equipements').then((m) => m.ImportEquipements),
      },
      {
        path: 'equipements/:id',
        title: 'Fiche équipement · MécaDétect',
        loadComponent: () =>
          import('./features/equipements/fiche-equipement').then((m) => m.FicheEquipement),
      },
      {
        path: 'equipements/:id/modifier',
        title: 'Modifier un équipement · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/equipements/formulaire-equipement').then((m) => m.FormulaireEquipement),
      },
      // Pages provisoires, remplacées aux sprints suivants
      {
        path: 'dashboard',
        title: 'Tableau de bord · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/dashboard/tableau-de-bord').then((m) => m.TableauDeBord),
      },
      {
        path: 'mes-interventions',
        title: 'Mes interventions · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['TECHNICIEN'] },
        loadComponent: () =>
          import('./features/interventions/mes-interventions').then((m) => m.MesInterventions),
      },
      {
        path: 'interventions',
        title: 'Interventions · MécaDétect',
        loadComponent: () =>
          import('./features/interventions/liste-interventions').then((m) => m.ListeInterventions),
      },
      {
        // Mêmes droits que l'API : seuls le demandeur et l'admin déclarent une panne
        path: 'interventions/declarer',
        title: 'Déclarer une panne · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['DEMANDEUR', 'ADMIN'] },
        loadComponent: () =>
          import('./features/interventions/declaration-panne').then((m) => m.DeclarationPanne),
      },
      {
        path: 'interventions/:id',
        title: 'Intervention · MécaDétect',
        loadComponent: () =>
          import('./features/interventions/detail-intervention').then((m) => m.DetailIntervention),
      },
      // La racine redirige vers la page d'accueil du rôle connecté
      { path: '', pathMatch: 'full', redirectTo: () => inject(SessionService).routeAccueil() },
    ],
  },
  { path: '**', redirectTo: '' },
];
