import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { BASE_PATH } from '../../core/api';
import { ListeEquipements } from './list-equipements';

describe('ListeEquipements', () => {
  let fixture: ComponentFixture<ListeEquipements>;
  let http: HttpTestingController;

  const pageVide = { page: 0, taille: 20, totalElements: 0, totalPages: 0, contenu: [] };
  const pageAvecUnEquipement = {
    page: 0,
    taille: 20,
    totalElements: 1,
    totalPages: 1,
    contenu: [
      {
        idEquipement: 1,
        reference: 'PRS-001',
        nom: 'Presse 250 tonnes',
        dateMiseService: '2016-03-14',
        statut: 'EN_SERVICE',
        type: { idType: 1, libelle: 'Presse hydraulique' },
        zone: { idZone: 1, code: 'A1', libelle: 'Atelier A' },
      },
    ],
  };

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ListeEquipements],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ListeEquipements);
    fixture.detectChanges();
    await fixture.whenStable();

    // Listes déroulantes chargées au démarrage
    http.expectOne('/api/v1/zones').flush([]);
    http.expectOne('/api/v1/types-equipement').flush([]);
  });

  afterEach(() => http.verify());

  function requeteRecherche() {
    return http.expectOne((requete) => requete.url === '/api/v1/equipements');
  }

  it('charge la première page triée par référence au démarrage', () => {
    const requete = requeteRecherche();

    expect(requete.request.params.get('page')).toBe('0');
    expect(requete.request.params.get('size')).toBe('20');
    expect(decodeURIComponent(requete.request.params.get('sort')!)).toBe('reference,asc');
    expect(requete.request.params.has('statut')).toBe(false);
    requete.flush(pageVide);
  });

  it('affiche les équipements reçus dans le tableau', async () => {
    requeteRecherche().flush(pageAvecUnEquipement);
    fixture.detectChanges();
    await fixture.whenStable();

    const texte = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texte).toContain('PRS-001');
    expect(texte).toContain('Presse 250 tonnes');
    expect(texte).toContain('En service');
  });

  it('relance la recherche avec le filtre choisi, en revenant à la première page', async () => {
    requeteRecherche().flush(pageVide);

    fixture.componentInstance.modifier({ statut: 'ARCHIVE', page: 0 });
    fixture.detectChanges();
    await fixture.whenStable();

    const requete = requeteRecherche();
    expect(requete.request.params.get('statut')).toBe('ARCHIVE');
    expect(requete.request.params.get('page')).toBe('0');
    requete.flush(pageVide);
  });

  it('affiche un message quand aucun équipement ne correspond', async () => {
    requeteRecherche().flush(pageVide);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucun équipement ne correspond');
  });
});
