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
      <a routerLink="/" class="marque">
        <!-- Colonne lumineuse d'atelier, en miniature : le repère visuel de l'application -->
        <span class="colonne-lumineuse" aria-hidden="true"><i></i><i></i><i></i></span>
        MécaDétect
      </a>

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

    /* Barre en bleu ardoise : les boutons Material y prennent des couleurs claires */
    .barre {
      /* Reste immobile pendant les transitions de page (voir styles.scss) */
      view-transition-name: barre;
      gap: 1rem;
      background: var(--md-encre, #1f2a37);
      color: #f8f9fa;
      --mat-sys-primary: #f8f9fa;
      --mat-sys-on-surface: #f8f9fa;
      --mat-sys-on-surface-variant: #c3cad2;
      --mat-sys-outline: rgb(255 255 255 / 0.35);
    }
    .marque {
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      margin-right: 1rem;
      font-family: var(--md-titre);
      font-size: 1.45rem;
      font-weight: 700;
      letter-spacing: 0.01em;
      text-decoration: none;
      color: inherit;
    }
    .colonne-lumineuse {
      display: inline-flex;
      flex-direction: column;
      gap: 2px;
      padding: 3px;
      border-radius: 3px;
      background: rgb(0 0 0 / 0.35);
    }
    .colonne-lumineuse i { width: 7px; height: 7px; border-radius: 50%; }
    .colonne-lumineuse i:nth-child(1) { background: #ef4444; }
    .colonne-lumineuse i:nth-child(2) { background: #f2a516; }
    .colonne-lumineuse i:nth-child(3) { background: #34a866; }

    nav { display: flex; gap: 0.25rem; }
    /* Les boutons Material tirent leur couleur de --mat-sys-primary */
    nav a { --mat-sys-primary: #c3cad2; }
    /* Page courante : texte blanc et trait clair, comme un onglet enfoncé */
    nav a { transition: box-shadow 200ms ease; }
    nav a.actif { --mat-sys-primary: #ffffff; box-shadow: inset 0 -3px 0 #f8f9fa; border-radius: 0; }
    .espace { flex: 1; }
    .compte { display: flex; align-items: center; gap: 1rem; }
    .utilisateur { font-size: 0.95rem; color: #c3cad2; }

    /* Menu des petits écrans (rendu hors de la barre, sur fond clair) */
    .menu-utilisateur {
      margin: 0;
      padding: 0.75rem 1rem;
      font-family: var(--md-titre);
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--md-encre, #1f2a37);
    }
    a[mat-menu-item].actif { box-shadow: inset 3px 0 0 var(--md-encre, #1f2a37); background: var(--mat-sys-secondary-container); }

    .contenu { padding: 1.75rem 1.5rem; max-width: 1400px; margin: 0 auto; }

    /* Grand écran : la barre complète suffit */
    @media (min-width: 600px) {
      .bouton-menu { display: none; }
    }

    /* Téléphone : marque et bouton menu seulement */
    @media (max-width: 599.98px) {
      .barre { gap: 0.5rem; padding: 0 0.5rem 0 1rem; }
      .liens, .compte { display: none; }
      .contenu { padding: 1.25rem 1rem; }
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
