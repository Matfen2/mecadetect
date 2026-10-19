/**
 * Signaux du tableau Andon (bande de voyants en tête du tableau de bord).
 * Comme sur un tableau lumineux d'atelier : rouge = un problème exige une action,
 * ambre = à surveiller, vert = situation normale. Fonctions pures, testées seules.
 */

export type Signal = 'alerte' | 'attention' | 'normal' | 'neutre';

interface CompteParPriorite {
  priorite: string;
  nombre: number;
}

/** Rouge si une intervention critique est ouverte, ambre si d'autres le sont, vert sinon. */
export function signalOuvertes(priorites: readonly CompteParPriorite[]): Signal {
  const critiques = priorites.find((p) => p.priorite === 'CRITIQUE')?.nombre ?? 0;
  if (critiques > 0) {
    return 'alerte';
  }
  const total = priorites.reduce((somme, p) => somme + p.nombre, 0);
  return total > 0 ? 'attention' : 'normal';
}

/** Un seul équipement en panne suffit à allumer le voyant rouge. */
export function signalPannes(equipementsEnPanne: number): Signal {
  return equipementsEnPanne > 0 ? 'alerte' : 'normal';
}

const LIBELLES: Record<Signal, string> = {
  alerte: 'alerte',
  attention: 'à surveiller',
  normal: 'normal',
  neutre: 'information',
};

/** Libellé lu par les lecteurs d'écran : la couleur seule ne doit jamais porter l'information. */
export function libelleSignal(signal: Signal): string {
  return LIBELLES[signal];
}
