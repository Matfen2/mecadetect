/** Libellés lisibles des énumérations d'intervention, partagés par les badges et les filtres. */
export const LIBELLES_STATUT_INTERVENTION: Record<string, string> = {
  DEMANDEE: 'Demandée',
  PLANIFIEE: 'Planifiée',
  EN_COURS: 'En cours',
  TERMINEE: 'Terminée',
  ANNULEE: 'Annulée',
};

export const LIBELLES_PRIORITE: Record<string, string> = {
  BASSE: 'Basse',
  NORMALE: 'Normale',
  HAUTE: 'Haute',
  CRITIQUE: 'Critique',
};

/** Options des listes déroulantes, dans l'ordre métier (et non alphabétique). */
export const OPTIONS_STATUT_INTERVENTION = Object.entries(LIBELLES_STATUT_INTERVENTION).map(
  ([valeur, libelle]) => ({ valeur, libelle }),
);
export const OPTIONS_PRIORITE = Object.entries(LIBELLES_PRIORITE).map(([valeur, libelle]) => ({ valeur, libelle }));
