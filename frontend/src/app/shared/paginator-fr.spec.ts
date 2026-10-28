import { PaginatorFr } from './paginator-fr';

describe('PaginatorFr', () => {
  const paginateur = new PaginatorFr();
  // Séparateur de milliers français : espace fine insécable (U+202F), comme le produit Intl
  const fine = ' ';

  it('affiche la plage de lignes en français', () => {
    expect(paginateur.getRangeLabel(0, 20, 17)).toBe('1 – 17 sur 17');
    expect(paginateur.getRangeLabel(1, 20, 45)).toBe('21 – 40 sur 45');
  });

  it('gère une liste vide', () => {
    expect(paginateur.getRangeLabel(0, 20, 0)).toBe('0 sur 0');
    expect(paginateur.itemsPerPageLabel).toBe('Lignes par page :');
  });

  it('traduit les libellés de navigation', () => {
    expect(paginateur.nextPageLabel).toBe('Page suivante');
    expect(paginateur.previousPageLabel).toBe('Page précédente');
    expect(paginateur.firstPageLabel).toBe('Première page');
    expect(paginateur.lastPageLabel).toBe('Dernière page');
  });

  it('sépare les milliers du total', () => {
    expect(paginateur.getRangeLabel(0, 25, 200000)).toBe(`1 – 25 sur 200${fine}000`);
  });

  it('sépare les milliers des bornes de la page', () => {
    expect(paginateur.getRangeLabel(399, 25, 200000)).toBe(
      `9${fine}976 – 10${fine}000 sur 200${fine}000`,
    );
    expect(paginateur.getRangeLabel(7999, 25, 200000)).toBe(
      `199${fine}976 – 200${fine}000 sur 200${fine}000`,
    );
  });

  it('sépare les milliers même avec une taille de page nulle', () => {
    expect(paginateur.getRangeLabel(0, 0, 1500)).toBe(`0 sur 1${fine}500`);
  });
});
