import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Nombres au format français : 200 000 (espace fine insécable comme séparateur de milliers). */
const nombre = new Intl.NumberFormat('fr-FR');
const formater = (valeur: number): string => nombre.format(valeur);

/** Textes du paginateur Angular Material en français, pour toute l'application. */
@Injectable()
export class PaginatorFr extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Lignes par page :';
  override nextPageLabel = 'Page suivante';
  override previousPageLabel = 'Page précédente';
  override firstPageLabel = 'Première page';
  override lastPageLabel = 'Dernière page';

  override getRangeLabel = (page: number, taille: number, total: number): string => {
    if (total === 0 || taille === 0) {
      return `0 sur ${formater(total)}`;
    }
    const debut = page * taille;
    // Page au-delà du total (liste réduite par un filtre) : même règle que Material
    const fin = debut < total ? Math.min(debut + taille, total) : debut + taille;
    return `${formater(debut + 1)} – ${formater(fin)} sur ${formater(total)}`;
  };
}
