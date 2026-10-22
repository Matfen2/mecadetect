import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { BASE_PATH } from '../../core/api';
import { SessionService } from '../../core/auth/session.service';
import { MesInterventions } from './mes-interventions';

describe('MesInterventions', () => {
  let fixture: ComponentFixture<MesInterventions>;
  let http: HttpTestingController;

  const lucas = { idUtilisateur: 3, nom: 'Bernard', prenom: 'Lucas', role: 'TECHNICIEN' };

  const enCours = {
    idIntervention: 41,
    numero: 'INT-2026-000041',
    titre: 'Bruit anormal du convoyeur',
    priorite: 'NORMALE',
    statut: 'EN_COURS',
    dateDemande: '2026-03-08T07:00:00Z',
    datePlanifiee: '2026-03-08T08:00:00Z',
    equipementReference: 'CNV-001',
    equipementNom: "Convoyeur d'assemblage principal",
    technicienNomComplet: 'Lucas Bernard',
  };
  const planifiee = {
    ...enCours,
    idIntervention: 42,
    numero: 'INT-2026-000042',
    titre: "Fuite d'huile",
    priorite: 'HAUTE',
    statut: 'PLANIFIEE',
    equipementReference: 'PRS-001',
    equipementNom: 'Presse 225 tonnes',
  };
  const page = (contenu: object[]) => ({ page: 0, taille: 50, totalElements: contenu.length, totalPages: 1, contenu });

  function detail(resume: typeof enCours, complement: object = {}) {
    return {
      ...resume,
      description: 'Description de la panne',
      version: 4,
      equipement: {},
      demandeur: lucas,
      technicien: lucas,
      historique: [],
      ...complement,
    };
  }

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [MesInterventions],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
        // Technicien connecté : la liste est demandée pour son identifiant
        { provide: SessionService, useValue: { utilisateur: () => lucas } },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MesInterventions);
    await rafraichir();
  });

  afterEach(() => http.verify());

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function chargerListe(contenu: object[]): Promise<void> {
    http.expectOne((r) => r.url === '/api/v1/techniciens/3/interventions').flush(page(contenu));
    await rafraichir();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function bouton(libelle: string): HTMLButtonElement | undefined {
    return Array.from(element().querySelectorAll('button')).find((b) => b.textContent!.trim() === libelle);
  }

  it('affiche les interventions du technicien, celle en cours mise en évidence', async () => {
    await chargerListe([enCours, planifiee]);

    const cartes = element().querySelectorAll('.carte');
    expect(cartes.length).toBe(2);
    expect(cartes[0].classList).toContain('en-cours');
    expect(cartes[0].textContent).toContain('CNV-001');
    expect(cartes[1].textContent).toContain("Fuite d'huile");
  });

  it('affiche un message quand rien ne lui est assigné', async () => {
    await chargerListe([]);
    expect(element().textContent).toContain('Aucune intervention ne vous est assignée');
  });

  it('démarre en 2 clics : Démarrer puis Confirmer le démarrage, avec la version du détail', async () => {
    await chargerListe([planifiee]);

    // Clic 1 : chargement du détail, aucune action envoyée
    bouton('Démarrer')!.click();
    http.expectOne('/api/v1/interventions/42').flush(detail(planifiee, { version: 1 }));
    await rafraichir();
    expect(element().textContent).toContain('Description de la panne');

    // Clic 2 : démarrage avec la version reçue, puis rechargement de la liste
    bouton('Confirmer le démarrage')!.click();
    const requete = http.expectOne('/api/v1/interventions/42/demarrage');
    expect(requete.request.body).toEqual({ version: 1 });
    requete.flush(detail(planifiee, { statut: 'EN_COURS', version: 2 }));
    await rafraichir();

    await chargerListe([{ ...planifiee, statut: 'EN_COURS' }]);
    expect(element().textContent).toContain('INT-2026-000042 démarrée.');
  });

  it('refus RG-04 : le message de l’API est affiché sur la carte', async () => {
    await chargerListe([planifiee]);
    bouton('Démarrer')!.click();
    http.expectOne('/api/v1/interventions/42').flush(detail(planifiee, { version: 1 }));
    await rafraichir();

    bouton('Confirmer le démarrage')!.click();
    http.expectOne('/api/v1/interventions/42/demarrage').flush(
      { status: 409, code: 'INTERVENTION_DEJA_EN_COURS', detail: 'Vous avez déjà une intervention en cours.' },
      { status: 409, statusText: 'Conflict' },
    );
    await rafraichir();

    expect(element().querySelector('.carte')!.textContent).toContain('Vous avez déjà une intervention en cours.');
    // L'action est refermée : les autres boutons redeviennent utilisables
    expect(bouton('Démarrer')!.disabled).toBe(false);
  });

  it('clôture en 2 clics, temps passé prérempli depuis le démarrage', async () => {
    await chargerListe([enCours]);
    const ilYa90Minutes = new Date(Date.now() - 90 * 60_000).toISOString();

    bouton('Clôturer')!.click();
    http.expectOne('/api/v1/interventions/41').flush(detail(enCours, { dateDebut: ilYa90Minutes }));
    await rafraichir();
    expect(fixture.componentInstance.cloture.controls.tempsPasseMin.value).toBe(90);

    fixture.componentInstance.cloture.controls.compteRendu.setValue('Roulement remplacé, bruit disparu');
    bouton('Valider la clôture')!.click();
    const requete = http.expectOne('/api/v1/interventions/41/cloture');
    expect(requete.request.body).toEqual({ version: 4, compteRendu: 'Roulement remplacé, bruit disparu', tempsPasseMin: 90 });
    requete.flush(detail(enCours, { statut: 'TERMINEE', version: 5 }));
    await rafraichir();

    await chargerListe([]);
    expect(element().textContent).toContain('INT-2026-000041 clôturée.');
  });

  it("une durée rapide remplit le temps passé d'un geste", async () => {
    await chargerListe([enCours]);
    bouton('Clôturer')!.click();
    http.expectOne('/api/v1/interventions/41').flush(detail(enCours));
    await rafraichir();

    bouton('30 min')!.click();
    expect(fixture.componentInstance.cloture.controls.tempsPasseMin.value).toBe(30);
  });

  it("la clôture n'est pas envoyée sans compte rendu suffisant (RG-03)", async () => {
    await chargerListe([enCours]);
    bouton('Clôturer')!.click();
    http.expectOne('/api/v1/interventions/41').flush(detail(enCours));
    await rafraichir();

    fixture.componentInstance.cloture.setValue({ compteRendu: 'Fait', tempsPasseMin: 20 });
    bouton('Valider la clôture')!.click();
    await rafraichir();

    expect(element().textContent).toContain('10 caractères minimum.');
    // http.verify() confirme qu'aucune clôture n'est partie
  });
});
