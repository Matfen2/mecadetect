import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { BASE_PATH } from '../../core/api';
import { FicheEquipement } from './fiche-equipement';

describe('FicheEquipement', () => {
  let fixture: ComponentFixture<FicheEquipement>;
  let http: HttpTestingController;

  const presse = {
    idEquipement: 5,
    reference: 'PRS-001',
    nom: 'Presse 250 tonnes',
    dateMiseService: '2016-03-14',
    statut: 'EN_SERVICE',
    type: { idType: 1, libelle: 'Presse hydraulique' },
    zone: { idZone: 1, code: 'A1', libelle: 'Atelier A - Emboutissage' },
  };

  /** Simule une session ouverte avec le rôle donné. */
  function connecter(role: string): void {
    sessionStorage.setItem(
      'mecadetect.session',
      JSON.stringify({
        token: 'jeton',
        expireLe: Date.now() + 3_600_000,
        utilisateur: { idUtilisateur: 1, nom: 'Martin', prenom: 'Claire', role },
      }),
    );
  }

  async function afficher(role: string): Promise<void> {
    connecter(role);
    await TestBed.configureTestingModule({
      imports: [FicheEquipement],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FicheEquipement);
    fixture.componentRef.setInput('id', '5');
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function texte(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function bouton(libelle: string): HTMLButtonElement | undefined {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button, a')).find((element) =>
      element.textContent?.includes(libelle),
    ) as HTMLButtonElement | undefined;
  }

  const pageHistorique = {
    page: 0,
    taille: 10,
    totalElements: 1,
    totalPages: 1,
    contenu: [
      {
        idIntervention: 42,
        numero: 'INT-2026-000042',
        titre: "Fuite d'huile",
        priorite: 'HAUTE',
        statut: 'TERMINEE',
        dateDemande: '2026-03-08T08:00:00Z',
        equipementReference: 'PRS-001',
        equipementNom: 'Presse 250 tonnes',
      },
    ],
  };

  /** Répond à la fiche, puis à l'historique des interventions qu'elle affiche. */
  async function charger(equipement: object, historique: object = pageHistorique): Promise<void> {
    http.expectOne('/api/v1/equipements/5').flush(equipement);
    await rafraichir();
    http.expectOne((r) => r.url === '/api/v1/equipements/5/interventions').flush(historique);
    await rafraichir();
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it("affiche les informations de l'équipement", async () => {
    await afficher('TECHNICIEN');
    await charger(presse);

    expect(texte()).toContain('PRS-001');
    expect(texte()).toContain('Presse 250 tonnes');
    expect(texte()).toContain('Presse hydraulique');
    expect(texte()).toContain('A1');
    expect(texte()).toContain('14/03/2016');
    expect(texte()).toContain('En service');
  });

  it("n'affiche les actions de modification qu'à l'administrateur", async () => {
    await afficher('TECHNICIEN');
    await charger(presse);

    expect(bouton('Modifier')).toBeUndefined();
    expect(bouton('Archiver')).toBeUndefined();
  });

  it("affiche un message clair quand l'équipement n'existe pas", async () => {
    await afficher('ADMIN');
    http
      .expectOne('/api/v1/equipements/5')
      .flush({ status: 404, code: 'EQUIPEMENT_INTROUVABLE', detail: "L'équipement 5 n'existe pas." }, { status: 404, statusText: 'Not Found' });
    await rafraichir();

    expect(texte()).toContain("Cet équipement n'existe pas");
  });

  it("archive l'équipement après confirmation, puis recharge la fiche", async () => {
    await afficher('ADMIN');
    await charger(presse);

    // Premier clic : demande de confirmation, aucun appel à l'API
    bouton('Archiver')!.click();
    await rafraichir();
    expect(texte()).toContain("Confirmer l'archivage");
    http.expectNone({ method: 'DELETE', url: '/api/v1/equipements/5' });

    // Confirmation : appel DELETE puis rechargement de la fiche
    bouton('Confirmer')!.click();
    await rafraichir();
    http.expectOne({ method: 'DELETE', url: '/api/v1/equipements/5' }).flush(null, { status: 204, statusText: 'No Content' });
    await rafraichir();
    await charger({ ...presse, statut: 'ARCHIVE' });

    expect(texte()).toContain('Archivé');
    expect(bouton('Archiver')).toBeUndefined();
  });

  it("affiche l'historique des interventions de l'équipement", async () => {
    await afficher('TECHNICIEN');
    await charger(presse);

    expect(texte()).toContain('INT-2026-000042');
    expect(texte()).toContain("Fuite d'huile");
    expect(texte()).toContain('Terminée');
  });

  it('propose au demandeur de déclarer une panne sur cet équipement', async () => {
    await afficher('DEMANDEUR');
    await charger(presse);

    const lien = bouton('Déclarer une panne') as unknown as HTMLAnchorElement;
    expect(lien.getAttribute('href')).toBe('/interventions/declarer?equipement=5');
  });

  it('ne propose pas la déclaration au technicien', async () => {
    await afficher('TECHNICIEN');
    await charger(presse);

    expect(bouton('Déclarer une panne')).toBeUndefined();
  });
});
