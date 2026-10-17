import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatToolbarModule } from '@angular/material/toolbar';

import { SessionService } from '../core/auth/session.service';
import { PaginatorFr } from '../shared/paginator-fr';

interface LienNavigation {
  libelle: string;
  route: string;
  roles?: string[];
}

const LIENS: LienNavigation[] = [
  { libelle: 'Tableau de bord', route: '/dashboard', roles: ['ADMIN'] },
  { libelle: 'Mes interventions', route: '/mes-interventions', roles: ['TECHNICIEN'] },
  { libelle: 'Interventions', route: '/interventions' },
  { libelle: 'Équipements', route: '/equipements' },
];

/**
 * Cadre commun des pages connectées : navigation selon le rôle et zone de contenu.
 * Sous 600 px, les liens, le nom et la déconnexion passent dans un menu (bouton ☰) :
 * la barre ne déborde plus, et le navigateur mobile n'a plus à dézoomer la page.
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
  ],
  // Paginateur en français pour toutes les pages connectées. Fourni ici plutôt que dans app.config :
  // le shell est chargé à la demande, le paginateur n'alourdit donc pas le bundle initial.
  providers: [{ provide: MatPaginatorIntl, useClass: PaginatorFr }],
  template: `
    <mat-toolbar class="barre">
      <a routerLink="/" class="marque">MécaDétect</a>

      <nav class="liens" aria-label="Navigation principale">
        @for (lien of liens(); track lien.route) {
          <a mat-button [routerLink]="lien.route" routerLinkActive="actif">{{ lien.libelle }}</a>
        }
      </nav>

      <span class="espace"></span>

      <span class="compte">
        @if (session.utilisateur(); as utilisateur) {
          <span class="utilisateur">{{ utilisateur.prenom }} {{ utilisateur.nom }}</span>
        }
        <button mat-stroked-button (click)="session.deconnecter()">Déconnexion</button>
      </span>

      <button mat-icon-button class="bouton-menu" [matMenuTriggerFor]="menu" aria-label="Ouvrir le menu">
        <mat-icon>menu</mat-icon>
      </button>
    </mat-toolbar>

    <mat-menu #menu="matMenu" xPosition="before">
      @if (session.utilisateur(); as utilisateur) {
        <p class="menu-utilisateur">{{ utilisateur.prenom }} {{ utilisateur.nom }}</p>
        <mat-divider />
      }
      @for (lien of liens(); track lien.route) {
        <a mat-menu-item [routerLink]="lien.route" routerLinkActive="actif">{{ lien.libelle }}</a>
      }
      <mat-divider />
      <button mat-menu-item (click)="session.deconnecter()">
        <mat-icon>logout</mat-icon>
        <span>Déconnexion</span>
      </button>
    </mat-menu>

    <main class="contenu">
      <router-outlet />
    </main>
  `,
  styles: `
    :host { display: block; }
    .barre { gap: 1rem; }
    .marque { font-weight: 600; text-decoration: none; color: inherit; margin-right: 1rem; }
    nav { display: flex; gap: 0.25rem; }
    .actif { background: var(--mat-sys-secondary-container); }
    .espace { flex: 1; }
    .compte { display: flex; align-items: center; gap: 1rem; }
    .utilisateur { font-size: 0.9rem; }
    .menu-utilisateur {
      margin: 0;
      padding: 0.75rem 1rem;
      font: var(--mat-sys-title-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .contenu { padding: 1.5rem; max-width: 1400px; margin: 0 auto; }

    /* Grand écran : la barre complète suffit */
    @media (min-width: 600px) {
      .bouton-menu { display: none; }
    }

    /* Téléphone : logo et bouton menu seulement */
    @media (max-width: 599.98px) {
      .barre { gap: 0.5rem; padding: 0 0.5rem 0 1rem; }
      .liens, .compte { display: none; }
      .contenu { padding: 1rem; }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly session = inject(SessionService);

  /** Liens visibles selon le rôle de l'utilisateur connecté. */
  protected readonly liens = computed(() => {
    const role = this.session.role();
    return LIENS.filter((lien) => !lien.roles || (role !== null && lien.roles.includes(role)));
  });
}
