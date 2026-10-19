import { duree, estMoisEnCours, heures, libelleMois, libelleMoisCourt, nombre, proportion, variation } from './indicateurs';

/** Les espaces insécables d'Intl sont remplacés par des espaces simples pour la lisibilité des tests. */
const texte = (valeur: string) => valeur.replace(/[  ]/g, ' ');

describe('indicateurs du tableau de bord', () => {
  it('proportion : pourcentage du maximum, borné, et 0 si le maximum est nul', () => {
    expect(proportion(50, 200)).toBe(25);
    expect(proportion(200, 200)).toBe(100);
    expect(proportion(300, 200)).toBe(100);
    expect(proportion(5, 0)).toBe(0);
  });

  it('mois : libellé long pour les infobulles, court pour l’axe', () => {
    expect(libelleMois('2026-10')).toBe('octobre 2026');
    expect(libelleMoisCourt('2026-01')).toBe('janv.');
  });

  it('variation : signée, avec un vrai signe moins, tiret sans mois précédent', () => {
    expect(texte(variation(327))).toBe('+327');
    expect(texte(variation(-5014))).toBe('−5 014');
    expect(variation(0)).toBe('0');
    expect(variation(null)).toBe('—');
    expect(variation(undefined)).toBe('—');
  });

  it('heures et durées au format français', () => {
    expect(texte(heures(42153))).toBe('703 h');
    expect(duree(44)).toBe('44 h');
    expect(duree(12.5)).toBe('12,5 h');
    expect(texte(nombre(20696))).toBe('20 696');
  });

  it('mois en cours, calculé en UTC comme l’API', () => {
    const maintenant = new Date(Date.UTC(2026, 9, 14, 12));
    expect(estMoisEnCours('2026-10', maintenant)).toBe(true);
    expect(estMoisEnCours('2026-09', maintenant)).toBe(false);
  });
});
