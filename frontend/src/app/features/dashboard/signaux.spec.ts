import { libelleSignal, signalOuvertes, signalPannes } from './signaux';

describe('Signaux du tableau Andon', () => {
  describe('interventions ouvertes', () => {
    it('passe au rouge dès qu’une intervention critique est ouverte', () => {
      expect(
        signalOuvertes([
          { priorite: 'NORMALE', nombre: 12 },
          { priorite: 'CRITIQUE', nombre: 1 },
        ]),
      ).toBe('alerte');
    });

    it('passe à l’ambre quand des interventions sont ouvertes, sans critique', () => {
      expect(
        signalOuvertes([
          { priorite: 'HAUTE', nombre: 3 },
          { priorite: 'CRITIQUE', nombre: 0 },
        ]),
      ).toBe('attention');
    });

    it('reste au vert quand rien n’est ouvert', () => {
      expect(signalOuvertes([{ priorite: 'NORMALE', nombre: 0 }])).toBe('normal');
      expect(signalOuvertes([])).toBe('normal');
    });
  });

  describe('équipements en panne', () => {
    it('passe au rouge dès qu’un équipement est en panne', () => {
      expect(signalPannes(1)).toBe('alerte');
    });

    it('reste au vert sans équipement en panne', () => {
      expect(signalPannes(0)).toBe('normal');
    });
  });

  it('donne un libellé à chaque signal, pour ceux qui ne voient pas la couleur', () => {
    expect(libelleSignal('alerte')).toBe('alerte');
    expect(libelleSignal('attention')).toBe('à surveiller');
    expect(libelleSignal('normal')).toBe('normal');
    expect(libelleSignal('neutre')).toBe('information');
  });
});
