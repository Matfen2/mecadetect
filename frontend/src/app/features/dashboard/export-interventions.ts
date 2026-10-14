import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { DashboardService } from '../../core/api';
import { MESSAGE_ERREUR_INATTENDUE, messageErreur } from '../../shared/probleme-api';
import { Telechargement } from '../../shared/telechargement';

/** Même limite que l'API : un export porte sur un an au plus. */
export const JOURS_MAX_EXPORT = 366;

/** Date du jour au format des champs date (AAAA-MM-JJ), à l'heure locale. */
function jour(date: Date): string {
  const mois = String(date.getMonth() + 1).padStart(2, '0');
  return `${date.getFullYear()}-${mois}-${String(date.getDate()).padStart(2, '0')}`;
}

/** Nombre de jours de la période, bornes incluses. */
export function joursInclus(depuis: string, jusqua: string): number {
  return Math.round((Date.parse(jusqua) - Date.parse(depuis)) / 86_400_000) + 1;
}

/**
 * Export CSV des interventions demandées sur une période (tâche 5.3), placé sur le tableau de bord.
 * La période est vérifiée avant l'envoi, avec les mêmes règles que l'API ; le fichier reçu est
 * proposé au téléchargement sous le nom « interventions-AAAA-MM-JJ-au-AAAA-MM-JJ.csv ».
 */
@Component({
  selector: 'app-export-interventions',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <form class="export" [formGroup]="formulaire" (ngSubmit)="exporter()" aria-label="Export CSV des interventions">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Demandées du</mat-label>
        <input matInput type="date" formControlName="depuis" />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>au</mat-label>
        <input matInput type="date" formControlName="jusqua" />
      </mat-form-field>
      <button mat-flat-button type="submit" [disabled]="enCours()">
        {{ enCours() ? 'Export en cours…' : 'Exporter en CSV' }}
      </button>
    </form>
    @if (erreur(); as message) {
      <p class="erreur" role="alert">{{ message }}</p>
    }
  `,
  styles: `
    .export { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
    mat-form-field { width: 11rem; }
    .erreur { margin: 0.25rem 0 0; color: var(--mat-sys-error, #ba1a1a); font-size: 0.9rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExportInterventions {
  private readonly dashboardApi = inject(DashboardService);
  private readonly telechargement = inject(Telechargement);

  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly formulaire = new FormGroup({
    // Par défaut, le mois en cours jusqu'à aujourd'hui
    depuis: new FormControl(jour(new Date(new Date().getFullYear(), new Date().getMonth(), 1)), {
      nonNullable: true,
      validators: Validators.required,
    }),
    jusqua: new FormControl(jour(new Date()), { nonNullable: true, validators: Validators.required }),
  });

  protected exporter(): void {
    const { depuis, jusqua } = this.formulaire.getRawValue();
    const probleme = this.verifier(depuis, jusqua);
    this.erreur.set(probleme);
    if (probleme) {
      return;
    }

    this.enCours.set(true);
    this.dashboardApi.exporterInterventions({ depuis, jusqua }).subscribe({
      next: (fichier) => {
        this.enCours.set(false);
        this.telechargement.enregistrer(fichier, `interventions-${depuis}-au-${jusqua}.csv`);
      },
      error: async (erreur: unknown) => {
        this.erreur.set(await ExportInterventions.message(erreur));
        this.enCours.set(false);
      },
    });
  }

  private verifier(depuis: string, jusqua: string): string | null {
    if (!depuis || !jusqua) {
      return 'Indiquez les deux dates de la période.';
    }
    if (jusqua < depuis) {
      return 'La date de fin doit être postérieure ou égale à la date de début.';
    }
    if (joursInclus(depuis, jusqua) > JOURS_MAX_EXPORT) {
      return `La période exportée est limitée à ${JOURS_MAX_EXPORT} jours.`;
    }
    return null;
  }

  /** En téléchargement, le corps d'une erreur arrive lui aussi sous forme de fichier : on le relit. */
  private static async message(erreur: unknown): Promise<string> {
    if (erreur instanceof HttpErrorResponse && erreur.error instanceof Blob) {
      try {
        const corps = JSON.parse(await erreur.error.text()) as { detail?: unknown };
        if (typeof corps.detail === 'string') {
          return corps.detail;
        }
      } catch {
        // Corps illisible : message générique
      }
      return MESSAGE_ERREUR_INATTENDUE;
    }
    return messageErreur(erreur);
  }
}
