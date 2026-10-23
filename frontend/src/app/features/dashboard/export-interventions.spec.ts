import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { BASE_PATH } from '../../core/api';
import { Telechargement } from '../../shared/telechargement';
import { ExportInterventions, joursInclus } from './export-interventions';

describe('ExportInterventions', () => {
  let fixture: ComponentFixture<ExportInterventions>;
  let http: HttpTestingController;
  let enregistrements: { contenu: Blob; nom: string }[];

  beforeEach(async () => {
    enregistrements = [];
    await TestBed.configureTestingModule({
      imports: [ExportInterventions],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: '/api/v1' },
        { provide: Telechargement, useValue: { enregistrer: (contenu: Blob, nom: string) => enregistrements.push({ contenu, nom }) } },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ExportInterventions);
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

  function champ(nom: 'depuis' | 'jusqua'): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`input[formcontrolname="${nom}"]`)!;
  }

  async function saisir(depuis: string, jusqua: string): Promise<void> {
    for (const [nom, valeur] of [['depuis', depuis], ['jusqua', jusqua]] as const) {
      champ(nom).value = valeur;
      champ(nom).dispatchEvent(new Event('input'));
    }
    await rafraichir();
  }

  async function exporter(): Promise<void> {
    element().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await rafraichir();
  }

  it('propose par défaut le mois en cours, du 1er à aujourd’hui', () => {
    const aujourdhui = new Date();
    const mm = String(aujourdhui.getMonth() + 1).padStart(2, '0');
    expect(champ('depuis').value).toBe(`${aujourdhui.getFullYear()}-${mm}-01`);
    expect(champ('jusqua').value).toBe(`${aujourdhui.getFullYear()}-${mm}-${String(aujourdhui.getDate()).padStart(2, '0')}`);
  });

  it('télécharge le fichier de la période, sous un nom explicite', async () => {
    await saisir('2026-01-01', '2026-03-31');
    await exporter();

    const requete = http.expectOne(
      (r) => r.url === '/api/v1/exports/interventions' && r.params.get('depuis') === '2026-01-01' && r.params.get('jusqua') === '2026-03-31',
    );
    expect(requete.request.responseType).toBe('blob');
    expect(element().querySelector('button')!.textContent).toContain('Export en cours');

    const fichier = new Blob(['﻿Numéro;Titre\r\n'], { type: 'text/csv' });
    requete.flush(fichier);
    await rafraichir();

    expect(enregistrements).toEqual([{ contenu: fichier, nom: 'interventions-2026-01-01-au-2026-03-31.csv' }]);
    expect(element().querySelector('button')!.textContent).toContain('Exporter en CSV');
  });

  it('refuse une période inversée sans appeler l’API', async () => {
    await saisir('2026-03-31', '2026-01-01');
    await exporter();

    expect(element().querySelector('[role="alert"]')!.textContent).toContain('postérieure ou égale');
  });

  it('refuse une période de plus de 366 jours, comme l’API', async () => {
    expect(joursInclus('2025-01-01', '2025-12-31')).toBe(365);
    await saisir('2025-01-01', '2026-01-02');
    await exporter();

    expect(element().querySelector('[role="alert"]')!.textContent).toContain('366 jours');
  });

  it('affiche le message de l’API, relu depuis le corps de l’erreur reçu en fichier', async () => {
    await saisir('2026-01-01', '2026-01-31');
    await exporter();

    const probleme = new Blob([JSON.stringify({ code: 'PERIODE_INVALIDE', detail: 'Période refusée par le serveur.' })], {
      type: 'application/problem+json',
    });
    http.expectOne((r) => r.url === '/api/v1/exports/interventions').flush(probleme, { status: 400, statusText: 'Bad Request' });
    await new Promise((resolve) => setTimeout(resolve));
    await rafraichir();

    expect(element().querySelector('[role="alert"]')!.textContent).toContain('Période refusée par le serveur.');
    expect(enregistrements).toEqual([]);
  });
});
