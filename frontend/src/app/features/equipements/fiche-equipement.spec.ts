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

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it("affiche les informations de l'équipement", async () => {
    await afficher('TECHNICIEN');
    http.expectOne('/api/v1/equipements/5').flush(presse);
    await rafraichir();

    expect(texte()).toContain('PRS-001');
    expect(texte()).toContain('Presse 250 tonnes');
    expect(texte()).toContain('Presse hydraulique');
    expect(texte()).toContain('A1');
    expect(texte()).toContain('14/03/2016');
    expect(texte()).toContain('En service');
  });

  it("n'affiche les actions de modification qu'à l'administrateur", async () => {
    await afficher('TECHNICIEN');
    http.expectOne('/api/v1/equipements/5').flush(presse);
    await rafraichir();

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
    http.expectOne('/api/v1/equipements/5').flush(presse);
    await rafraichir();

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
    http.expectOne('/api/v1/equipements/5').flush({ ...presse, statut: 'ARCHIVE' });
    await rafraichir();

    expect(texte()).toContain('Archivé');
    expect(bouton('Archiver')).toBeUndefined();
  });
});
