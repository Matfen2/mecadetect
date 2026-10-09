import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { authGuard, roleGuard, visiteurGuard } from './core/auth/auth.guards';
import { SessionService } from './core/auth/session.service';

const pageProvisoire = () => import('./features/accueil/accueil').then((m) => m.Accueil);

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
        // Déclarée avant :id, sinon « nouveau » serait lu comme un identifiant
        path: 'equipements/nouveau',
        title: 'Nouvel équipement · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/equipements/formulaire-equipement').then((m) => m.FormulaireEquipement),
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
        data: { roles: ['ADMIN'], titre: 'Tableau de bord' },
        loadComponent: pageProvisoire,
      },
      {
        path: 'mes-interventions',
        title: 'Mes interventions · MécaDétect',
        canActivate: [roleGuard],
        data: { roles: ['TECHNICIEN'], titre: 'Mes interventions' },
        loadComponent: pageProvisoire,
      },
      {
        path: 'interventions',
        title: 'Interventions · MécaDétect',
        data: { titre: 'Interventions' },
        loadComponent: pageProvisoire,
      },
      // La racine redirige vers la page d'accueil du rôle connecté
      { path: '', pathMatch: 'full', redirectTo: () => inject(SessionService).routeAccueil() },
    ],
  },
  { path: '**', redirectTo: '' },
];
