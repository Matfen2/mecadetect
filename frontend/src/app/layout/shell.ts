import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';

import { SessionService } from '../core/auth/session.service';

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

/** Cadre commun des pages connectées : navigation selon le rôle et zone de contenu. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  template: `
    <mat-toolbar class="barre">
      <a routerLink="/" class="marque">MécaDétect</a>
      <nav>
        @for (lien of liens(); track lien.route) {
          <a mat-button [routerLink]="lien.route" routerLinkActive="actif">{{ lien.libelle }}</a>
        }
      </nav>
      <span class="espace"></span>
      @if (session.utilisateur(); as utilisateur) {
        <span class="utilisateur">{{ utilisateur.prenom }} {{ utilisateur.nom }}</span>
      }
      <button mat-stroked-button (click)="session.deconnecter()">Déconnexion</button>
    </mat-toolbar>

    <main class="contenu">
      <router-outlet />
    </main>
  `,
  styles: `
    .barre { gap: 1rem; }
    .marque { font-weight: 600; text-decoration: none; color: inherit; margin-right: 1rem; }
    nav { display: flex; gap: 0.25rem; }
    .actif { background: var(--mat-sys-secondary-container); }
    .espace { flex: 1; }
    .utilisateur { font-size: 0.9rem; }
    .contenu { padding: 1.5rem; max-width: 1400px; margin: 0 auto; }
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
