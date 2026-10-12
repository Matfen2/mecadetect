import { HttpErrorResponse } from '@angular/common/http';

import { MESSAGE_ERREUR_INATTENDUE, lireProbleme, messageErreur } from './probleme-api';

describe('probleme-api', () => {
  it("lit le Problem Details renvoyé par l'API", () => {
    const erreur = new HttpErrorResponse({
      status: 409,
      error: { status: 409, code: 'REFERENCE_DEJA_UTILISEE', detail: 'La référence PRS-001 est déjà utilisée.' },
    });

    expect(lireProbleme(erreur)?.code).toBe('REFERENCE_DEJA_UTILISEE');
    expect(messageErreur(erreur)).toBe('La référence PRS-001 est déjà utilisée.');
  });

  it('renvoie un message générique si la réponse ne vient pas de notre API', () => {
    const erreur = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    expect(lireProbleme(erreur)).toBeNull();
    expect(messageErreur(erreur)).toBe(MESSAGE_ERREUR_INATTENDUE);
  });
});
