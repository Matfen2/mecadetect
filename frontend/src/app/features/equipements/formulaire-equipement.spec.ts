import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { BASE_PATH } from '../../core/api';
import { FormulaireEquipement } from './formulaire-equipement';

describe('FormulaireEquipement', () => {
  let fixture: ComponentFixture<FormulaireEquipement>;
  let http: HttpTestingController;
  let navigation: ReturnType<typeof vi.spyOn>;

  const zones = [{ idZone: 1, code: 'A1', libelle: 'Atelier A - Emboutissage' }];
  const types = [{ idType: 1, libelle: 'Presse hydraulique' }];
  const presse = {
    idEquipement: 5,
    reference: 'PRS-001',
    nom: 'Presse 250 tonnes',
    dateMiseService: '2016-03-14',
    statut: 'EN_SERVICE',
    type: types[0],
    zone: zones[0],
  };
  const saisieValide = {
    reference: 'PRS-010',
    nom: 'Presse 500 tonnes',
    dateMiseService: '2026-01-15',
    idType: 1,
    idZone: 1,
  };

  /** Ouvre le formulaire : sans identifiant en création, avec un identifiant en modification. */
  async function ouvrir(id?: string): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [FormulaireEquipement],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(FormulaireEquipement);
    if (id) {
      fixture.componentRef.setInput('id', id);
    }
    fixture.detectChanges();
    await fixture.whenStable();

    http.expectOne('/api/v1/zones').flush(zones);
    http.expectOne('/api/v1/types-equipement').flush(types);
  }

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function composant() {
    return fixture.componentInstance;
  }

  function texte(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  afterEach(() => http.verify());

  it("n'envoie rien tant que le formulaire est invalide", async () => {
    await ouvrir();

    composant().enregistrer();
    await rafraichir();

    http.expectNone({ method: 'POST', url: '/api/v1/equipements' });
    expect(texte()).toContain('La référence est obligatoire');
  });

  it("crée l'équipement puis ouvre sa fiche", async () => {
    await ouvrir();

    composant().formulaire.setValue({ ...saisieValide, reference: '  PRS-010  ' });
    composant().enregistrer();

    const requete = http.expectOne({ method: 'POST', url: '/api/v1/equipements' });
    expect(requete.request.body).toEqual(saisieValide);
    requete.flush({ ...presse, idEquipement: 21, reference: 'PRS-010' }, { status: 201, statusText: 'Created' });
    await rafraichir();

    expect(navigation).toHaveBeenCalledWith(['/equipements', 21]);
  });

  it("pré-remplit le formulaire en modification et envoie un PUT", async () => {
    await ouvrir('5');
    http.expectOne({ method: 'GET', url: '/api/v1/equipements/5' }).flush(presse);
    await rafraichir();

    expect(composant().formulaire.getRawValue()).toEqual({
      reference: 'PRS-001',
      nom: 'Presse 250 tonnes',
      dateMiseService: '2016-03-14',
      idType: 1,
      idZone: 1,
    });

    composant().formulaire.controls.nom.setValue('Presse 250 tonnes (révisée)');
    composant().enregistrer();

    const requete = http.expectOne({ method: 'PUT', url: '/api/v1/equipements/5' });
    expect(requete.request.body.nom).toBe('Presse 250 tonnes (révisée)');
    requete.flush(presse);
    await rafraichir();

    expect(navigation).toHaveBeenCalledWith(['/equipements', 5]);
  });

  it('affiche sous le bon champ une référence déjà utilisée (409)', async () => {
    await ouvrir();

    composant().formulaire.setValue(saisieValide);
    composant().enregistrer();
    http.expectOne({ method: 'POST', url: '/api/v1/equipements' }).flush(
      { status: 409, code: 'REFERENCE_DEJA_UTILISEE', detail: 'La référence PRS-010 est déjà utilisée.' },
      { status: 409, statusText: 'Conflict' },
    );
    await rafraichir();

    expect(composant().formulaire.controls.reference.hasError('serveur')).toBe(true);
    expect(texte()).toContain('La référence PRS-010 est déjà utilisée.');
    expect(navigation).not.toHaveBeenCalled();
  });

  it("répartit les erreurs de validation de l'API sur les champs concernés (400)", async () => {
    await ouvrir();

    composant().formulaire.setValue(saisieValide);
    composant().enregistrer();
    http.expectOne({ method: 'POST', url: '/api/v1/equipements' }).flush(
      {
        status: 400,
        code: 'DONNEES_INVALIDES',
        detail: 'Certains champs sont invalides.',
        erreurs: [{ champ: 'nom', message: 'la taille doit être comprise entre 1 et 100' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await rafraichir();

    expect(composant().formulaire.controls.nom.hasError('serveur')).toBe(true);
    expect(texte()).toContain('la taille doit être comprise entre 1 et 100');
  });
});
