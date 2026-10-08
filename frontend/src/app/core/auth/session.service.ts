import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { AuthService, LoginResponse, UtilisateurResume } from '../api';

interface Session {
  token: string;
  /** Date d'expiration du jeton, en millisecondes depuis l'epoch */
  expireLe: number;
  utilisateur: UtilisateurResume;
}

const CLE_STOCKAGE = 'mecadetect.session';

/**
 * Gère la session de l'utilisateur connecté.
 * Le nom SessionService évite le conflit avec AuthService, généré depuis le contrat OpenAPI.
 *
 * Stockage en sessionStorage : la session disparaît à la fermeture de l'onglet,
 * ce qui limite la durée d'exposition du jeton.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly authApi = inject(AuthService);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(this.lireSession());

  readonly utilisateur = computed(() => this.session()?.utilisateur ?? null);
  readonly role = computed(() => (this.session()?.utilisateur.role as string | undefined) ?? null);
  readonly estConnecte = computed(() => this.session() !== null);

  /** Jeton valide, ou null s'il est absent ou expiré. */
  get jeton(): string | null {
    const session = this.session();
    return session && session.expireLe > Date.now() ? session.token : null;
  }

  connecter(email: string, motDePasse: string): Observable<LoginResponse> {
    return this.authApi.login({ loginRequest: { email, motDePasse } }).pipe(
      tap((reponse) =>
        this.enregistrer({
          token: reponse.token,
          expireLe: Date.now() + reponse.expireDans * 1000,
          utilisateur: reponse.utilisateur,
        }),
      ),
    );
  }

  deconnecter(): void {
    sessionStorage.removeItem(CLE_STOCKAGE);
    this.session.set(null);
    void this.router.navigate(['/login']);
  }

  /** Page d'accueil selon le rôle de l'utilisateur connecté. */
  routeAccueil(): string {
    switch (this.role()) {
      case 'ADMIN':
        return '/dashboard';
      case 'TECHNICIEN':
        return '/mes-interventions';
      default:
        return '/interventions';
    }
  }

  private enregistrer(session: Session): void {
    sessionStorage.setItem(CLE_STOCKAGE, JSON.stringify(session));
    this.session.set(session);
  }

  private lireSession(): Session | null {
    try {
      const brut = sessionStorage.getItem(CLE_STOCKAGE);
      if (!brut) {
        return null;
      }
      const session = JSON.parse(brut) as Session;
      return session.expireLe > Date.now() ? session : null;
    } catch {
      return null;
    }
  }
}
