import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session.service';

/** Réservé aux utilisateurs connectés. Sinon : page de connexion, avec retour prévu. */
export const authGuard: CanActivateFn = (_route, etat) => {
  const session = inject(SessionService);
  const router = inject(Router);

  return session.jeton
    ? true
    : router.createUrlTree(['/login'], { queryParams: { retour: etat.url } });
};

/**
 * Réservé aux rôles listés dans data.roles de la route.
 * Un rôle non autorisé est renvoyé vers sa propre page d'accueil.
 * Le back-end reste la vraie protection : ce guard sert le confort de navigation.
 */
export const roleGuard: CanActivateFn = (route) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const rolesAutorises = (route.data['roles'] as string[] | undefined) ?? [];

  return rolesAutorises.length === 0 || rolesAutorises.includes(session.role() ?? '')
    ? true
    : router.createUrlTree([session.routeAccueil()]);
};

/** Page de connexion : un utilisateur déjà connecté est renvoyé vers son accueil. */
export const visiteurGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  return session.jeton ? router.createUrlTree([session.routeAccueil()]) : true;
};
