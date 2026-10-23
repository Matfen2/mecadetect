/** Action proposée sur la page de détail d'une intervention. */
export type ActionIntervention = 'planifier' | 'demarrer' | 'cloturer' | 'annuler';

/** Ce dont la règle a besoin : le statut et le technicien assigné. */
export interface InterventionPourActions {
  statut: string;
  technicien?: { idUtilisateur: number } | null;
}

/** L'utilisateur connecté : son rôle et son identifiant. */
export interface UtilisateurPourActions {
  role: string | null;
  idUtilisateur: number | null;
}

/**
 * Actions affichées selon le rôle et le statut (tâche 3.9).
 * Elles reprennent les règles de l'API (RG-01 cycle de vie, RG-02 seul le technicien assigné agit) :
 * un bouton n'apparaît que si l'API acceptera l'action. Le serveur reste le seul juge,
 * cette fonction évite seulement de proposer une action vouée à l'échec.
 */
export function actionsPossibles(
  intervention: InterventionPourActions,
  utilisateur: UtilisateurPourActions,
): ActionIntervention[] {
  const statut = intervention.statut;

  if (utilisateur.role === 'ADMIN') {
    const actions: ActionIntervention[] = [];
    if (statut === 'DEMANDEE') {
      actions.push('planifier');
    }
    if (statut === 'DEMANDEE' || statut === 'PLANIFIEE') {
      actions.push('annuler');
    }
    return actions;
  }

  const estAssigne =
    utilisateur.role === 'TECHNICIEN' &&
    utilisateur.idUtilisateur !== null &&
    intervention.technicien?.idUtilisateur === utilisateur.idUtilisateur;

  if (estAssigne && statut === 'PLANIFIEE') {
    return ['demarrer'];
  }
  if (estAssigne && statut === 'EN_COURS') {
    return ['cloturer'];
  }
  return [];
}
