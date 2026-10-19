import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { BASE_PATH } from '../../core/api';
import { TableauDeBord } from './tableau-de-bord';

/** Mois « AAAA-MM » décalé de n mois par rapport au mois en cours (UTC, comme l'API). */
function mois(decalage: number): string {
  const maintenant = new Date();
  const date = new Date(Date.UTC(maintenant.getUTCFullYear(), maintenant.getUTCMonth() + decalage, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

const texte = (element: Element | null) => (element?.textContent ?? '').replace(/[  ]/g, ' ').trim();

describe('TableauDeBord', () => {
  let fixture: ComponentFixture<TableauDeBord>;
  let http: HttpTestingController;

  const tableau = {
    ouvertesParPriorite: [
      { priorite: 'CRITIQUE', nombre: 348 },
      { priorite: 'HAUTE', nombre: 936 },
      { priorite: 'NORMALE', nombre: 1773 },
      { priorite: 'BASSE', nombre: 878 },
    ],
    equipementsEnPanne: 16,
    tempsMoyenResolutionHeures: 44,
    topEquipements: [
      { rang: 1, reference: 'PRS-001', nom: 'Presse 225 tonnes', nombrePannes: 20696 },
      { rang: 2, reference: 'PRS-002', nom: 'Presse 400 tonnes', nombrePannes: 8574 },
    ],
    evolutionMensuelle: Array.from({ length: 12 }, (_, i) => ({
      mois: mois(i - 11),
      nombre: i === 11 ? 3139 : 8000 + i,
      variation: i === 0 ? undefined : 1,
    })),
    chargeTechniciens: [
      {
        technicien: { idUtilisateur: 2, nom: 'Bernard', prenom: 'Lucas', role: 'TECHNICIEN' },
        interventionsMois: 246,
        tempsPasseMinMois: 42153,
      },
      {
        technicien: { idUtilisateur: 27, nom: 'André', prenom: 'Sarah', role: 'TECHNICIEN' },
        interventionsMois: 123,
        tempsPasseMinMois: 600,
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TableauDeBord],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: BASE_PATH, useValue: '/api/v1' }],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TableauDeBord);
    await rafraichir();
  });

  afterEach(() => http.verify());

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function charger(): Promise<void> {
    http.expectOne('/api/v1/dashboard').flush(tableau);
    await rafraichir();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche une barre de progression pendant le chargement', async () => {
    expect(element().querySelector('mat-progress-bar')).not.toBeNull();
    await charger();
    expect(element().querySelector('mat-progress-bar')).toBeNull();
  });

  it('affiche les trois chiffres clés : ouvertes (total des priorités), équipements en panne, temps moyen', async () => {
    await charger();

    expect(texte(element().querySelector('[data-indicateur="ouvertes"] .valeur'))).toBe('3 935');
    expect(texte(element().querySelector('[data-indicateur="pannes"] .valeur'))).toBe('16');
    expect(texte(element().querySelector('[data-indicateur="resolution"] .valeur'))).toBe('44 h');
  });

  it('présente les priorités de la plus urgente à la moins urgente, barres proportionnelles au maximum', async () => {
    await charger();

    const lignes = Array.from(element().querySelectorAll<HTMLElement>('[data-priorite]'));
    expect(lignes.map((l) => texte(l.querySelector('.libelle')))).toEqual(['Critique', 'Haute', 'Normale', 'Basse']);
    const largeur = (l: HTMLElement) => l.querySelector<HTMLElement>('.barre')!.style.width;
    expect(largeur(lignes[2])).toBe('100%');
    expect(parseFloat(largeur(lignes[0]))).toBeCloseTo((348 / 1773) * 100, 1);
  });

  it('classe les équipements les plus en panne avec leur rang', async () => {
    await charger();

    const lignes = element().querySelectorAll('.top .ligne');
    expect(lignes.length).toBe(2);
    expect(texte(lignes[0].querySelector('.rang'))).toBe('1');
    expect(texte(lignes[0].querySelector('.libelle'))).toContain('PRS-001');
    expect(texte(lignes[0].querySelector('.nombre'))).toBe('20 696');
  });

  it('trace 12 mois et distingue le mois en cours, incomplet', async () => {
    await charger();

    const colonnes = element().querySelectorAll('.colonne');
    expect(colonnes.length).toBe(12);
    expect(colonnes[11].classList).toContain('en-cours');
    expect(colonnes[10].classList).not.toContain('en-cours');
    expect(texte(colonnes[11].querySelector('.info'))).toContain('mois en cours');
    // Version tableau pour les lecteurs d'écran
    expect(element().querySelectorAll('table.visuellement-cache tbody tr').length).toBe(12);
  });

  it('liste la charge des techniciens ce mois-ci, du plus chargé au moins chargé, temps passé en heures', async () => {
    // L'API les classe par interventions ouvertes : l'écran les reclasse par interventions terminées
    tableau.chargeTechniciens.reverse();
    await charger();
    tableau.chargeTechniciens.reverse();

    const lignes = element().querySelectorAll('table.charge tbody tr');
    expect(lignes.length).toBe(2);
    expect(texte(lignes[0].querySelector('td'))).toBe('Lucas Bernard');
    expect(texte(lignes[0].querySelector('.valeur-barre'))).toBe('246');
    expect(texte(lignes[0].querySelectorAll('td')[2])).toBe('703 h');
  });

  it('en cas d’erreur, affiche le message de l’API et permet de réessayer', async () => {
    http
      .expectOne('/api/v1/dashboard')
      .flush({ code: 'ERREUR', detail: 'Service indisponible.' }, { status: 503, statusText: 'Unavailable' });
    await rafraichir();

    expect(texte(element().querySelector('[role="alert"] p'))).toBe('Service indisponible.');
    const reessayer = Array.from(element().querySelectorAll('button')).find((b) => texte(b) === 'Réessayer')!;
    reessayer.click();
    await rafraichir();

    await charger();
    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(element().querySelectorAll('.colonne').length).toBe(12);
  });
});
