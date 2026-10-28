import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { SessionService } from '../../core/auth/session.service';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  /** Paramètre ?retour= de l'URL, renseigné par authGuard */
  readonly retour = input<string>();

  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  readonly formulaire = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', [Validators.required, Validators.minLength(8)]],
  });

  seConnecter(): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }

    this.chargement.set(true);
    this.erreur.set(null);
    const { email, motDePasse } = this.formulaire.getRawValue();

    this.session.connecter(email, motDePasse).subscribe({
      next: () => void this.router.navigateByUrl(this.retour() ?? this.session.routeAccueil()),
      error: (erreur: HttpErrorResponse) => {
        this.chargement.set(false);
        this.erreur.set(
          erreur.status === 401
            ? 'Email ou mot de passe incorrect.'
            : 'Le serveur est injoignable. Réessayez dans un instant.',
        );
      },
    });
  }
}
