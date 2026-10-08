import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { BASE_PATH } from '../api';
import { SessionService } from './session.service';

describe('SessionService', () => {
  let service: SessionService;
  let http: HttpTestingController;

  const reponseLogin = (role: string) => ({
    token: 'jeton-de-test',
    expireDans: 3600,
    utilisateur: { idUtilisateur: 1, nom: 'Martin', prenom: 'Claire', role },
  });

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Route attrape-tout : la déconnexion peut naviguer vers /login sans erreur
        provideRouter([{ path: '**', children: [] }]),
        { provide: BASE_PATH, useValue: '/api/v1' },
      ],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function seConnecterEnTantQue(role: string): void {
    service.connecter('claire@mecadetect.fr', 'Demo2026!').subscribe();
    const requete = http.expectOne('/api/v1/auth/login');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({ email: 'claire@mecadetect.fr', motDePasse: 'Demo2026!' });
    requete.flush(reponseLogin(role));
  }

  it("n'est pas connecté au départ", () => {
    expect(service.estConnecte()).toBe(false);
    expect(service.jeton).toBeNull();
  });

  it('enregistre la session après une connexion réussie', () => {
    seConnecterEnTantQue('ADMIN');

    expect(service.estConnecte()).toBe(true);
    expect(service.jeton).toBe('jeton-de-test');
    expect(service.utilisateur()?.prenom).toBe('Claire');
    expect(sessionStorage.getItem('mecadetect.session')).not.toBeNull();
  });

  it('redirige chaque rôle vers sa page d’accueil', () => {
    seConnecterEnTantQue('ADMIN');
    expect(service.routeAccueil()).toBe('/dashboard');

    seConnecterEnTantQue('TECHNICIEN');
    expect(service.routeAccueil()).toBe('/mes-interventions');

    seConnecterEnTantQue('DEMANDEUR');
    expect(service.routeAccueil()).toBe('/interventions');
  });

  it('vide la session à la déconnexion', () => {
    seConnecterEnTantQue('ADMIN');

    service.deconnecter();

    expect(service.estConnecte()).toBe(false);
    expect(service.jeton).toBeNull();
    expect(sessionStorage.getItem('mecadetect.session')).toBeNull();
  });

  it('ne garde pas de session en cas d’échec de connexion', () => {
    service.connecter('claire@mecadetect.fr', 'MauvaisMotDePasse').subscribe({ error: () => {} });
    http
      .expectOne('/api/v1/auth/login')
      .flush({ code: 'AUTHENTIFICATION_ECHOUEE' }, { status: 401, statusText: 'Unauthorized' });

    expect(service.estConnecte()).toBe(false);
  });
});
