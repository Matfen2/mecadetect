/** Mise en forme des indicateurs du tableau de bord (tâche 5.2) : fonctions pures, testées seules. */

const NOMBRE = new Intl.NumberFormat('fr-FR');
const DECIMAL = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });
const MOIS_LONG = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric', timeZone: 'UTC' });
const MOIS_COURT = new Intl.DateTimeFormat('fr-FR', { month: 'short', timeZone: 'UTC' });

/** Nombre entier au format français (espace insécable comme séparateur de milliers). */
export function nombre(valeur: number): string {
  return NOMBRE.format(valeur);
}

/** Part d'une valeur par rapport au maximum, en pourcentage borné à [0 ; 100]. */
export function proportion(valeur: number, maximum: number): number {
  if (maximum <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(0, (valeur / maximum) * 100));
}

function premierJour(mois: string): Date {
  const [annee, numero] = mois.split('-').map(Number);
  return new Date(Date.UTC(annee, numero - 1, 1));
}

/** « 2026-10 » → « octobre 2026 ». */
export function libelleMois(mois: string): string {
  return MOIS_LONG.format(premierJour(mois));
}

/** « 2026-10 » → « oct. », pour l'axe du graphique. */
export function libelleMoisCourt(mois: string): string {
  return MOIS_COURT.format(premierJour(mois));
}

/** Écart avec le mois précédent, signé : « +327 », « −51 », « 0 » ; tiret si pas de mois précédent. */
export function variation(valeur: number | null | undefined): string {
  if (valeur === null || valeur === undefined) {
    return '—';
  }
  if (valeur > 0) {
    return '+' + nombre(valeur);
  }
  return valeur < 0 ? '−' + nombre(-valeur) : '0';
}

/** Minutes → heures arrondies : 42 153 min → « 703 h ». */
export function heures(minutes: number): string {
  return nombre(Math.round(minutes / 60)) + ' h';
}

/** Durée en heures avec au plus une décimale : 44 → « 44 h », 12.5 → « 12,5 h ». */
export function duree(heuresMoyennes: number): string {
  return DECIMAL.format(heuresMoyennes) + ' h';
}

/** Le mois (« AAAA-MM ») est-il le mois en cours ? Calcul en UTC, comme l'API. */
export function estMoisEnCours(mois: string, maintenant: Date = new Date()): boolean {
  const courant = `${maintenant.getUTCFullYear()}-${String(maintenant.getUTCMonth() + 1).padStart(2, '0')}`;
  return mois === courant;
}
