import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { BASE_PATH } from '../../core/api';
import { DetailIntervention } from './detail-intervention';

describe('DetailIntervention', () => {
  let fixture: ComponentFixture<DetailIntervention>;
  let http: HttpTestingController;

  const claire = { idUtilisateur: 1, nom: 'Martin', prenom: 'Claire', role: 'ADMIN' };
  const sophie = { idUtilisateur: 2, nom: 'Moreau', prenom: 'Sophie', role: 'DEMANDEUR' };
  const lucas = { idUtilisateur: 3, nom: 'Bernard', prenom: 'Lucas', role: 'TECHNICIEN' };

  const demandee = {
    idIntervention: 42,
    numero: 'INT-2026-000042',
    titre: "Fuite d'huile",
    description: 'Fuite sur le vérin principal',
    priorite: 'HAUTE',
    statut: 'DEMANDEE',
    dateDemande: '2026-03-08T08:00:00Z',
    version: 0,
    equipement: {
      idEquipement: 5,
      reference: 'PRS-001',
      nom: 'Presse 250 tonnes',
      dateMiseService: '2016-03-14',
      statut: 'EN_PANNE',
      type: { idType: 1, libelle: 'Presse hydraulique' },
      zone: { idZone: 1, code: 'A1', libelle: 'Atelier A' },
    },
    demandeur: sophie,
    historique: [
      { nouveauStatut: 'DEMANDEE', dateChangement: '2026-03-08T08:00:00Z', auteur: sophie },
    ],
  };
  const planifiee = { ...demandee, statut: 'PLANIFIEE', version: 1, technicien: lucas, datePlanifiee: '2026-03-09T07:00:00Z' };
  const enCours = { ...planifiee, statut: 'EN_COURS', version: 2, dateDebut: '2026-03-09T07:05:00Z' };

  function connecter(utilisateur: typeof claire): void {
    sessionStorage.setItem(
      'mecadetect.session',
      JSON.stringify({ token: 'jeton', expireLe: Date.now() + 3_600_000, utilisateur }),
    );
  }

  async function afficher(utilisateur: typeof claire, intervention: object = demandee): Promise<void> {
    sessionStorage.clear();
    connecter(utilisateur);
    await TestBed.configureTestingModule({
      imports: [DetailIntervention],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DetailIntervention);
    fixture.componentRef.setInput('id', '42');
    await rafraichir();
    http.expectOne('/api/v1/interventions/42').flush(intervention);
    await rafraichir();
  }

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function texte(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function boutons(): string[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.actions button')).map(
      (bouton) => bouton.textContent!.trim(),
    );
  }

  function statut(): string {
    return (fixture.nativeElement as HTMLElement).querySelector('app-statut-intervention-badge')!.textContent!.trim();
  }

  afterEach(() => http.verify());

  it("affiche l'intervention, son équipement et son historique", async () => {
    await afficher(sophie);

    expect(texte()).toContain('INT-2026-000042');
    expect(texte()).toContain('PRS-001 · Presse 250 tonnes');
    expect(texte()).toContain('Sophie Moreau');
    expect(texte()).toContain('Haute');
    expect(statut()).toBe('Demandée');
  });

  it("le demandeur n'a aucune action", async () => {
    await afficher(sophie);
    expect(boutons()).toEqual([]);
  });

  it("affiche un message si l'intervention n'existe pas", async () => {
    sessionStorage.clear();
    connecter(sophie);
    await TestBed.configureTestingModule({
      imports: [DetailIntervention],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: BASE_PATH, useValue: '/api/v1' }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DetailIntervention);
    fixture.componentRef.setInput('id', '999');
    await rafraichir();
    http.expectOne('/api/v1/interventions/999').flush(
      { status: 404, code: 'INTERVENTION_INTROUVABLE', detail: "L'intervention 999 n'existe pas." },
      { status: 404, statusText: 'Not Found' },
    );
    await rafraichir();

    expect(texte()).toContain("Cette intervention n'existe pas.");
  });

  it("l'admin peut planifier ou annuler une demande", async () => {
    await afficher(claire);
    expect(boutons()).toEqual(['Planifier', "Annuler l'intervention"]);
  });

  it("l'admin planifie : techniciens habilités sur le type, version et date envoyées", async () => {
    await afficher(claire);
    fixture.componentInstance.ouvrir('planifier');
    await rafraichir();

    const requeteTechniciens = http.expectOne((r) => r.url === '/api/v1/techniciens');
    expect(requeteTechniciens.request.params.get('idType')).toBe('1');
    requeteTechniciens.flush([
      { idUtilisateur: 3, nom: 'Bernard', prenom: 'Lucas', habilitations: [], interventionsOuvertes: 1 },
    ]);

    fixture.componentInstance.planification.setValue({ idTechnicien: 3, datePlanifiee: '2026-03-09T08:00' });
    fixture.componentInstance.planifier();

    const requete = http.expectOne('/api/v1/interventions/42/planifier');
    expect(requete.request.body.version).toBe(0);
    expect(requete.request.body.idTechnicien).toBe(3);
    expect(requete.request.body.datePlanifiee).toBe(new Date('2026-03-09T08:00').toISOString());
    requete.flush(planifiee);
    await rafraichir();

    expect(statut()).toBe('Planifiée');
    expect(texte()).toContain('Lucas Bernard');
    expect(boutons()).toEqual(["Annuler l'intervention"]);
  });

  it('le technicien assigné démarre en un clic', async () => {
    await afficher(lucas, planifiee);
    expect(boutons()).toEqual(["Démarrer l'intervention"]);

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.actions button')!.click();
    const requete = http.expectOne('/api/v1/interventions/42/demarrer');
    expect(requete.request.body).toEqual({ version: 1 });
    requete.flush(enCours);
    await rafraichir();

    expect(statut()).toBe('En cours');
    expect(boutons()).toEqual(['Clôturer']);
  });

  it("la clôture n'est pas envoyée sans compte rendu suffisant (RG-03)", async () => {
    await afficher(lucas, enCours);
    fixture.componentInstance.ouvrir('cloturer');
    fixture.componentInstance.cloture.setValue({ compteRendu: 'Fait', tempsPasseMin: 30 });

    fixture.componentInstance.cloturer();
    await rafraichir();

    expect(texte()).toContain('10 caractères minimum.');
    // http.verify() confirme qu'aucune clôture n'est partie
  });

  it('la clôture envoie le compte rendu et le temps passé', async () => {
    await afficher(lucas, enCours);
    fixture.componentInstance.ouvrir('cloturer');
    fixture.componentInstance.cloture.setValue({ compteRendu: '  Joint du vérin remplacé  ', tempsPasseMin: 90 });

    fixture.componentInstance.cloturer();
    const requete = http.expectOne('/api/v1/interventions/42/cloturer');
    expect(requete.request.body).toEqual({ version: 2, compteRendu: 'Joint du vérin remplacé', tempsPasseMin: 90 });
    requete.flush({ ...enCours, statut: 'TERMINEE', version: 3, compteRendu: 'Joint du vérin remplacé', tempsPasseMin: 90, dateFin: '2026-03-09T08:35:00Z' });
    await rafraichir();

    expect(statut()).toBe('Terminée');
    expect(texte()).toContain('1 h 30');
  });

  it("un conflit de version propose de recharger l'intervention (UC-DB6)", async () => {
    await afficher(claire);
    fixture.componentInstance.ouvrir('annuler');
    fixture.componentInstance.annulation.setValue({ motif: 'Doublon de la demande 41' });

    fixture.componentInstance.annuler();
    http.expectOne('/api/v1/interventions/42/annuler').flush(
      { status: 409, code: 'CONFLIT_VERSION', detail: "L'intervention a été modifiée entre-temps." },
      { status: 409, statusText: 'Conflict' },
    );
    await rafraichir();
    expect(texte()).toContain("L'intervention a été modifiée entre-temps.");

    const recharger = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
      b.textContent!.includes("Recharger l'intervention"),
    )!;
    recharger.click();
    await rafraichir();

    http.expectOne('/api/v1/interventions/42').flush(planifiee);
    await rafraichir();
    expect(statut()).toBe('Planifiée');
  });
});
