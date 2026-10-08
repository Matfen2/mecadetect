import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';

import { SessionService } from '../../core/auth/session.service';

/**
 * Page provisoire : confirme la connexion et la redirection selon le rôle.
 * Elle sera remplacée par les vrais écrans aux sprints suivants.
 */
@Component({
  selector: 'app-accueil',
  imports: [MatButtonModule, MatToolbarModule],
  template: `
    <mat-toolbar>
      <span>MécaDétect</span>
      <span class="espace"></span>
      @if (session.utilisateur(); as utilisateur) {
        <span class="utilisateur">{{ utilisateur.prenom }} {{ utilisateur.nom }} · {{ utilisateur.role }}</span>
      }
      <button mat-stroked-button (click)="session.deconnecter()">Déconnexion</button>
    </mat-toolbar>

    <main class="contenu">
      <h1>{{ titre() }}</h1>
      <p>Connexion réussie. Cet écran sera développé dans un prochain sprint.</p>
    </main>
  `,
  styles: `
    .espace { flex: 1; }
    .utilisateur { font-size: 0.9rem; margin-right: 1rem; }
    .contenu { padding: 2rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accueil {
  protected readonly session = inject(SessionService);

  /** Renseigné par data.titre de la route */
  readonly titre = input<string>('');
}
