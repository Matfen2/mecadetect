import { Routes } from '@angular/router';

import { authGuard, roleGuard, visiteurGuard } from './core/auth/auth.guards';

const pageAccueil = () => import('./features/accueil/accueil').then((m) => m.Accueil);

export const routes: Routes = [
  {
    path: 'login',
    title: 'Connexion · MécaDétect',
    canActivate: [visiteurGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  // Pages provisoires : elles seront remplacées par les vrais écrans aux sprints suivants
  {
    path: 'dashboard',
    title: 'Tableau de bord · MécaDétect',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'], titre: 'Tableau de bord' },
    loadComponent: pageAccueil,
  },
  {
    path: 'mes-interventions',
    title: 'Mes interventions · MécaDétect',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIEN'], titre: 'Mes interventions' },
    loadComponent: pageAccueil,
  },
  {
    path: 'interventions',
    title: 'Interventions · MécaDétect',
    canActivate: [authGuard],
    data: { titre: 'Interventions' },
    loadComponent: pageAccueil,
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
