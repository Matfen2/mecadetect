import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * Propose un fichier reçu de l'API au téléchargement, comme un lien « Enregistrer sous ».
 * Isolé dans un service pour que les composants restent testables sans manipuler le DOM du navigateur.
 */
@Injectable({ providedIn: 'root' })
export class Telechargement {
  private readonly document = inject(DOCUMENT);

  enregistrer(contenu: Blob, nomFichier: string): void {
    const url = URL.createObjectURL(contenu);
    const lien = this.document.createElement('a');
    lien.href = url;
    lien.download = nomFichier;
    lien.click();
    // Libéré après le démarrage du téléchargement
    setTimeout(() => URL.revokeObjectURL(url));
  }
}
