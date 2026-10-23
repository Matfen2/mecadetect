import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { SessionService } from './session.service';

/**
 * - Ajoute le jeton JWT aux requêtes vers notre API (et uniquement vers elle,
 *   pour ne jamais l'envoyer à un site tiers).
 * - Si l'API répond 401 (jeton expiré ou invalide), déconnecte l'utilisateur
 *   et le renvoie vers la page de connexion.
 */
export const authInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const session = inject(SessionService);
  const jeton = session.jeton;
  const versNotreApi = requete.url.startsWith('/api/');

  const requeteFinale =
    jeton && versNotreApi
      ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } })
      : requete;

  return suivant(requeteFinale).pipe(
    catchError((erreur: unknown) => {
      const estLogin = requete.url.endsWith('/auth/login');
      if (erreur instanceof HttpErrorResponse && erreur.status === 401 && !estLogin) {
        session.deconnecter();
      }
      return throwError(() => erreur);
    }),
  );
};
