import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail } from '../core/api';

/** Message affiché quand l'API ne donne pas d'explication exploitable. */
export const MESSAGE_ERREUR_INATTENDUE = 'Une erreur inattendue est survenue. Réessayez dans quelques instants.';

/**
 * Lit le corps d'une erreur de l'API au format Problem Details (RFC 9457).
 * Renvoie null si la réponse n'en est pas une (serveur injoignable, erreur réseau...).
 */
export function lireProbleme(erreur: unknown): ProblemDetail | null {
  if (!(erreur instanceof HttpErrorResponse) || !erreur.error || typeof erreur.error !== 'object') {
    return null;
  }
  const corps = erreur.error as ProblemDetail;
  // Une erreur réseau donne aussi un objet (ProgressEvent) : on vérifie qu'il vient bien de l'API
  return typeof corps.detail === 'string' || typeof corps.code === 'string' ? corps : null;
}

/** Message lisible pour l'utilisateur : le détail fourni par l'API, ou un message générique. */
export function messageErreur(erreur: unknown): string {
  return lireProbleme(erreur)?.detail ?? MESSAGE_ERREUR_INATTENDUE;
}
