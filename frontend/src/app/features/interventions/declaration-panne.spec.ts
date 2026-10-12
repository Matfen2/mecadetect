import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { BASE_PATH } from '../../core/api';
import { DeclarationPanne } from './declaration-panne';

describe('DeclarationPanne', () => {
  let fixture: ComponentFixture<DeclarationPanne>;
  let http: HttpTestingController;
  let navigation: ReturnType<typeof vi.spyOn>;

  const equipements = {
    page: 0,
    taille: 100,
    totalElements: 1,
    totalPages: 1,
    contenu: [
      {
        idEquipement: 5,
        reference: 'PRS-001',
        nom: 'Presse 250 tonnes',
        dateMiseService: '2016-03-14',
        statut: 'EN_SERVICE',
        type: { idType: 1, libelle: 'Presse hydraulique' },
        zone: { idZone: 1, code: 'A1', libelle: 'Atelier A' },
      },
    ],
  };

  async function afficher(equipement?: string): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [DeclarationPanne],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(DeclarationPanne);
    if (equipement) {
      fixture.componentRef.setInput('equipement', equipement);
    }
    await rafraichir();
    http.expectOne((r) => r.url === '/api/v1/equipements').flush(equipements);
    await rafraichir();
  }

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function remplir(): void {
    fixture.componentInstance.formulaire.setValue({
      idEquipement: 5,
      titre: "Fuite d'huile",
      description: 'Fuite sur le vérin principal depuis ce matin',
      priorite: 'HAUTE',
    });
  }

  function texte(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  afterEach(() => http.verify());

  it('n’envoie rien tant que le formulaire est invalide et affiche les messages', async () => {
    await afficher();
    fixture.componentInstance.formulaire.controls.description.setValue('Court');

    fixture.componentInstance.declarer();
    await rafraichir();

    expect(texte()).toContain("Choisissez l'équipement en panne.");
    expect(texte()).toContain('Le titre est obligatoire.');
    expect(texte()).toContain('10 caractères minimum.');
    // http.verify() confirme qu'aucune déclaration n'est partie
  });

  it('priorité NORMALE par défaut', async () => {
    await afficher();
    expect(fixture.componentInstance.formulaire.controls.priorite.value).toBe('NORMALE');
  });

  it('présélectionne l’équipement passé dans l’URL', async () => {
    await afficher('5');
    expect(fixture.componentInstance.formulaire.controls.idEquipement.value).toBe(5);
  });

  it('déclare la panne puis ouvre le détail de l’intervention créée', async () => {
    await afficher();
    remplir();

    fixture.componentInstance.declarer();
    const requete = http.expectOne('/api/v1/interventions');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({
      idEquipement: 5,
      titre: "Fuite d'huile",
      description: 'Fuite sur le vérin principal depuis ce matin',
      priorite: 'HAUTE',
    });
    requete.flush({ idIntervention: 42 }, { status: 201, statusText: 'Created' });

    expect(navigation).toHaveBeenCalledWith(['/interventions', 42]);
  });

  it('affiche sous le champ équipement le refus d’un équipement archivé (RG-07)', async () => {
    await afficher();
    remplir();

    fixture.componentInstance.declarer();
    http.expectOne('/api/v1/interventions').flush(
      { status: 409, code: 'EQUIPEMENT_ARCHIVE', detail: "L'équipement PRS-001 est archivé." },
      { status: 409, statusText: 'Conflict' },
    );
    await rafraichir();

    expect(fixture.componentInstance.formulaire.controls.idEquipement.hasError('serveur')).toBe(true);
    expect(texte()).toContain("L'équipement PRS-001 est archivé.");
    expect(navigation).not.toHaveBeenCalled();
  });

  it('affiche une erreur générale si le serveur est injoignable', async () => {
    await afficher();
    remplir();

    fixture.componentInstance.declarer();
    http.expectOne('/api/v1/interventions').error(new ProgressEvent('error'));
    await rafraichir();

    expect(texte()).toContain('Une erreur inattendue est survenue');
  });
});
