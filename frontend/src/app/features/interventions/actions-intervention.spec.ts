import { actionsPossibles } from './actions-intervention';

describe('actionsPossibles', () => {
  const admin = { role: 'ADMIN', idUtilisateur: 1 };
  const lucas = { role: 'TECHNICIEN', idUtilisateur: 3 };
  const hugo = { role: 'TECHNICIEN', idUtilisateur: 4 };
  const demandeur = { role: 'DEMANDEUR', idUtilisateur: 2 };

  const demandee = { statut: 'DEMANDEE', technicien: null };
  const planifiee = { statut: 'PLANIFIEE', technicien: { idUtilisateur: 3 } };
  const enCours = { statut: 'EN_COURS', technicien: { idUtilisateur: 3 } };
  const terminee = { statut: 'TERMINEE', technicien: { idUtilisateur: 3 } };

  it("l'admin planifie ou annule une demande", () => {
    expect(actionsPossibles(demandee, admin)).toEqual(['planifier', 'annuler']);
  });

  it("l'admin peut encore annuler une intervention planifiée, plus la replanifier", () => {
    expect(actionsPossibles(planifiee, admin)).toEqual(['annuler']);
  });

  it("l'admin n'a plus d'action une fois l'intervention démarrée (RG-01)", () => {
    expect(actionsPossibles(enCours, admin)).toEqual([]);
  });

  it('le technicien assigné démarre puis clôture', () => {
    expect(actionsPossibles(planifiee, lucas)).toEqual(['demarrer']);
    expect(actionsPossibles(enCours, lucas)).toEqual(['cloturer']);
  });

  it("un autre technicien n'a aucune action (RG-02)", () => {
    expect(actionsPossibles(planifiee, hugo)).toEqual([]);
    expect(actionsPossibles(enCours, hugo)).toEqual([]);
  });

  it('aucune action sur une intervention terminée, ni pour le demandeur', () => {
    expect(actionsPossibles(terminee, lucas)).toEqual([]);
    expect(actionsPossibles(demandee, demandeur)).toEqual([]);
  });
});
