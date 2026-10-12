import { PaginatorFr } from './paginator-fr';

describe('PaginatorFr', () => {
  const paginateur = new PaginatorFr();

  it('affiche la plage de lignes en français', () => {
    expect(paginateur.getRangeLabel(0, 20, 17)).toBe('1 – 17 sur 17');
    expect(paginateur.getRangeLabel(1, 20, 45)).toBe('21 – 40 sur 45');
  });

  it('gère une liste vide', () => {
    expect(paginateur.getRangeLabel(0, 20, 0)).toBe('0 sur 0');
    expect(paginateur.itemsPerPageLabel).toBe('Lignes par page :');
  });
});
