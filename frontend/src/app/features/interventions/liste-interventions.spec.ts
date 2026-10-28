import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { BASE_PATH } from '../../core/api';
import { ListeInterventions } from './liste-interventions';

describe('ListeInterventions', () => {
  let fixture: ComponentFixture<ListeInterventions>;
  let http: HttpTestingController;

  const pageVide = { page: 0, taille: 20, totalElements: 0, totalPages: 0, contenu: [] };
  const pageAvecUneIntervention = {
    page: 0,
    taille: 20,
    totalElements: 1,
    totalPages: 1,
    contenu: [
      {
        idIntervention: 42,
        numero: 'INT-2026-000042',
        titre: 'Fuite hydraulique',
        priorite: 'CRITIQUE',
        statut: 'EN_COURS',
        dateDemande: '2026-03-08T08:00:00Z',
        equipementReference: 'PRS-001',
        equipementNom: 'Presse 250 tonnes',
        technicienNomComplet: 'Lucas Bernard',
      },
    ],
  };

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
    sessionStorage.clear();
    connecter(role);
    await TestBed.configureTestingModule({
      imports: [ListeInterventions],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BASE_PATH, useValue: '/api/v1' },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ListeInterventions);
    await rafraichir();
    if (role === 'ADMIN') {
      http.expectOne('/api/v1/techniciens').flush([]);
    }
  }

  async function rafraichir(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function requeteRecherche() {
    return http.expectOne((requete) => requete.url === '/api/v1/interventions');
  }

  function texte(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  afterEach(() => http.verify());

  it('charge la première page, demandes les plus récentes d’abord', async () => {
    await afficher('DEMANDEUR');
    const requete = requeteRecherche();

    expect(requete.request.params.get('page')).toBe('0');
    expect(requete.request.params.get('size')).toBe('20');
    expect(decodeURIComponent(requete.request.params.get('sort')!)).toBe('dateDemande,desc');
    expect(requete.request.params.has('statut')).toBe(false);
    requete.flush(pageVide);
  });

  it('affiche les interventions avec leurs badges et le technicien assigné', async () => {
    await afficher('DEMANDEUR');
    requeteRecherche().flush(pageAvecUneIntervention);
    await rafraichir();

    expect(texte()).toContain('INT-2026-000042');
    expect(texte()).toContain('PRS-001');
    expect(texte()).toContain('Critique');
    expect(texte()).toContain('En cours');
    expect(texte()).toContain('Lucas Bernard');
  });

  it('filtre par statut et revient à la première page', async () => {
    await afficher('DEMANDEUR');
    requeteRecherche().flush(pageVide);

    fixture.componentInstance.modifier({ page: 3 });
    await rafraichir();
    requeteRecherche().flush(pageVide);

    fixture.componentInstance.modifier({ statut: 'PLANIFIEE', page: 0 });
    await rafraichir();
    const requete = requeteRecherche();
    expect(requete.request.params.get('statut')).toBe('PLANIFIEE');
    expect(requete.request.params.get('page')).toBe('0');
    requete.flush(pageVide);
  });

  it('envoie la période saisie, bornes au format date', async () => {
    await afficher('DEMANDEUR');
    requeteRecherche().flush(pageVide);

    const champ = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[name="depuis"]')!;
    champ.value = '2026-03-01';
    champ.dispatchEvent(new Event('change'));
    await rafraichir();

    const requete = requeteRecherche();
    expect(requete.request.params.get('depuis')).toBe('2026-03-01');
    expect(requete.request.params.has('jusqua')).toBe(false);
    requete.flush(pageVide);
  });

  it('propose de déclarer une panne au demandeur', async () => {
    await afficher('DEMANDEUR');
    requeteRecherche().flush(pageVide);
    await rafraichir();

    expect(texte()).toContain('Déclarer une panne');
  });

  it('ne propose ni la déclaration ni le filtre technicien au technicien', async () => {
    await afficher('TECHNICIEN');
    requeteRecherche().flush(pageVide);
    await rafraichir();

    expect(texte()).not.toContain('Déclarer une panne');
    // http.verify() confirme qu'aucun appel à /techniciens (réservé à l'admin) n'a été fait
  });

  it('charge les techniciens pour le filtre de l’admin', async () => {
    await afficher('ADMIN');
    requeteRecherche().flush(pageVide);
    await rafraichir();

    fixture.componentInstance.modifier({ idTechnicien: 7, page: 0 });
    await rafraichir();
    expect(requeteRecherche().request.params.get('idTechnicien')).toBe('7');
  });

  it('ouvre le détail au clic sur une ligne', async () => {
    await afficher('DEMANDEUR');
    requeteRecherche().flush(pageAvecUneIntervention);
    await rafraichir();

    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('tr.ligne')!.click();

    expect(navigation).toHaveBeenCalledWith(['/interventions', 42]);
  });
});
