import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { BASE_PATH, RapportImport } from '../../core/api';
import { Telechargement } from '../../shared/telechargement';
import { ImportEquipements, TAILLE_MAX_OCTETS } from './import-equipements';

describe('ImportEquipements', () => {
  let fixture: ComponentFixture<ImportEquipements>;
  let http: HttpTestingController;
  let enregistrements: { contenu: Blob; nom: string }[];

  const rapportAvecRejets: RapportImport = {
    lignesLues: 9,
    importes: 5,
    rejetes: 4,
    rejets: [
      { ligne: 6, motif: 'La zone « L9 » n’existe pas.' },
      { ligne: 7, motif: 'La référence « PRS-001 » est déjà utilisée par un équipement existant.' },
      { ligne: 8, motif: 'La date de mise en service « 31/02/2024 » est invalide.' },
      { ligne: 9, motif: 'Ligne illisible : 5 colonnes séparées par des points-virgules sont attendues.' },
    ],
  };

  beforeEach(async () => {
    enregistrements = [];
    await TestBed.configureTestingModule({
      imports: [ImportEquipements],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
        { provide: Telechargement, useValue: { enregistrer: (contenu: Blob, nom: string) => enregistrements.push({ contenu, nom }) } },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ImportEquipements);
    await rafraichir();
  });

  afterEach(() => http.verify());

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function boutonImporter(): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  }

  function alerte(): string {
    return element().querySelector('[role="alert"]')?.textContent ?? '';
  }

  /** Simule le choix d'un fichier dans le sélecteur du navigateur. */
  async function choisir(fichier: File): Promise<void> {
    const champ = element().querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(champ, 'files', { value: [fichier], configurable: true });
    champ.dispatchEvent(new Event('change'));
    await rafraichir();
  }

  async function importer(): Promise<void> {
    boutonImporter().click();
    await rafraichir();
  }

  function csv(nom = 'parc.csv', contenu = 'reference;nom;date_mise_service;type;zone\n'): File {
    return new File([contenu], nom, { type: 'text/csv' });
  }

  it('présente les colonnes attendues et n’active l’import qu’une fois un fichier choisi', async () => {
    expect(element().textContent).toContain('reference;nom;date_mise_service;type;zone');
    expect(boutonImporter().disabled).toBe(true);

    await choisir(csv());

    expect(boutonImporter().disabled).toBe(false);
    expect(element().textContent).toContain('parc.csv');
  });

  it('envoie le fichier en multipart et affiche le rapport, lignes rejetées comprises', async () => {
    const fichier = csv();
    await choisir(fichier);
    await importer();

    const requete = http.expectOne('/api/v1/equipements/imports');
    expect(requete.request.method).toBe('POST');
    expect((requete.request.body as FormData).get('fichier')).toBe(fichier);
    expect(boutonImporter().textContent).toContain('Import en cours');

    requete.flush(rapportAvecRejets);
    await rafraichir();

    const chiffres = [...element().querySelectorAll('.chiffre strong')].map((e) => e.textContent?.trim());
    expect(chiffres).toEqual(['9', '5', '4']);

    const lignes = [...element().querySelectorAll('table.rejets tbody tr')];
    expect(lignes).toHaveLength(4);
    expect(lignes[0].textContent).toContain('6');
    expect(lignes[0].textContent).toContain('La zone « L9 » n’existe pas.');
  });

  it('annonce un import complet quand aucune ligne n’est rejetée', async () => {
    await choisir(csv());
    await importer();

    http.expectOne('/api/v1/equipements/imports').flush({ lignesLues: 3, importes: 3, rejetes: 0, rejets: [] });
    await rafraichir();

    expect(element().textContent).toContain('Toutes les lignes ont été importées.');
    expect(element().querySelector('table.rejets')).toBeNull();
  });

  it('signale les rejets non détaillés au-delà des 100 premiers', async () => {
    await choisir(csv());
    await importer();

    http.expectOne('/api/v1/equipements/imports').flush({ ...rapportAvecRejets, rejetes: 150 });
    await rafraichir();

    expect(element().textContent).toContain('Seules les 4 premières lignes rejetées sont détaillées.');
  });

  it('refuse un fichier qui n’est pas un CSV, sans appeler l’API', async () => {
    await choisir(new File(['x'], 'parc.xlsx'));

    expect(alerte()).toContain('fichier .csv');
    expect(boutonImporter().disabled).toBe(true);
  });

  it('refuse un fichier de plus de 2 Mo, comme l’API', async () => {
    const gros = csv();
    Object.defineProperty(gros, 'size', { value: TAILLE_MAX_OCTETS + 1 });
    await choisir(gros);

    expect(alerte()).toContain('2 Mo');
    expect(boutonImporter().disabled).toBe(true);
  });

  it('affiche le message de l’API en cas de refus', async () => {
    await choisir(csv());
    await importer();

    http
      .expectOne('/api/v1/equipements/imports')
      .flush(
        { code: 'FICHIER_VIDE', detail: 'Le fichier ne contient aucune ligne de données sous la ligne d’en-tête.' },
        { status: 400, statusText: 'Bad Request' },
      );
    await rafraichir();

    expect(alerte()).toContain('aucune ligne de données');
    expect(boutonImporter().textContent).toContain('Importer');
  });

  it('propose un modèle de fichier à télécharger', async () => {
    element().querySelector<HTMLButtonElement>('button.modele')!.click();
    await rafraichir();

    expect(enregistrements).toHaveLength(1);
    expect(enregistrements[0].nom).toBe('modele-import-equipements.csv');
    expect(await enregistrements[0].contenu.text()).toContain('reference;nom;date_mise_service;type;zone');
  });
});
