import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, finalize, map, of, startWith, switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { InterventionDetail, InterventionResume, InterventionsService, PageIntervention } from '../../core/api';
import { SessionService } from '../../core/auth/session.service';
import { PrioriteBadge } from '../../shared/priorite-badge';
import { messageErreur } from '../../shared/probleme-api';
import { StatutInterventionBadge } from '../../shared/statut-intervention-badge';

type EtatListe =
  | { statut: 'chargement' }
  | { statut: 'succes'; page: PageIntervention }
  | { statut: 'erreur' };

/** Intervention sur laquelle le technicien a engagé une action (premier clic). */
interface Selection {
  idIntervention: number;
  action: 'demarrer' | 'cloturer';
  /** Détail chargé au premier clic : il fournit la version pour le verrouillage optimiste. */
  detail: InterventionDetail | null;
}

/** Durées proposées en un geste sur tablette, en minutes. */
const DUREES_RAPIDES = [15, 30, 60, 90, 120];

/**
 * « Mes interventions » (tâche 3.10) : les interventions planifiées et en cours du technicien connecté,
 * celle en cours en premier. Pensée pour la tablette : grandes cibles tactiles, une colonne.
 *
 * Démarrage et clôture en 2 clics :
 * - Démarrer → Confirmer le démarrage ;
 * - Clôturer → (compte rendu, temps passé prérempli) → Valider la clôture.
 * Le premier clic charge le détail : la version reçue est celle envoyée à l'API (UC-DB6).
 */
@Component({
  selector: 'app-mes-interventions',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    PrioriteBadge,
    StatutInterventionBadge,
  ],
  templateUrl: './mes-interventions.html',
  styleUrl: './mes-interventions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MesInterventions {
  private readonly interventionsApi = inject(InterventionsService);
  private readonly session = inject(SessionService);

  protected readonly dureesRapides = DUREES_RAPIDES;

  private readonly rechargement = signal(0);
  protected readonly etat = signal<EtatListe>({ statut: 'chargement' });

  protected readonly selection = signal<Selection | null>(null);
  protected readonly envoiEnCours = signal(false);
  /** Erreur affichée sur une carte (idIntervention) ou en bas de liste (null). */
  protected readonly erreur = signal<{ idIntervention: number | null; message: string } | null>(null);
  /** Confirmation affichée après une action réussie. */
  protected readonly succes = signal<string | null>(null);
  private readonly tentatives = signal(0);

  readonly cloture = new FormGroup({
    compteRendu: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10), Validators.maxLength(2000)],
    }),
    tempsPasseMin: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1), Validators.max(999999)],
    }),
  });

  constructor() {
    toObservable(this.rechargement)
      .pipe(
        switchMap(() => this.chargerListe()),
        takeUntilDestroyed(),
      )
      .subscribe((etat) => this.etat.set(etat));
  }

  /**
   * Interventions du technicien connecté : son identifiant vient de la session,
   * et le back-end vérifie qu'il correspond bien au jeton.
   */
  private chargerListe(): Observable<EtatListe> {
    const idTechnicien = this.session.utilisateur()?.idUtilisateur;
    if (idTechnicien == null) {
      return of<EtatListe>({ statut: 'erreur' });
    }
    return this.interventionsApi.listerInterventionsTechnicien({ idTechnicien, page: 0, size: 50 }).pipe(
      map((page): EtatListe => ({ statut: 'succes', page })),
      catchError(() => of<EtatListe>({ statut: 'erreur' })),
      startWith<EtatListe>({ statut: 'chargement' }),
    );
  }

  /** Premier clic : charge le détail (et sa version) puis affiche la confirmation ou le formulaire. */
  preparer(intervention: InterventionResume, action: Selection['action']): void {
    this.erreur.set(null);
    this.succes.set(null);
    this.selection.set({ idIntervention: intervention.idIntervention, action, detail: null });
    this.interventionsApi.obtenirIntervention({ idIntervention: intervention.idIntervention }).subscribe({
      next: (detail) => {
        if (action === 'cloturer') {
          this.cloture.reset({ compteRendu: '', tempsPasseMin: minutesDepuis(detail.dateDebut) });
        }
        this.selection.set({ idIntervention: intervention.idIntervention, action, detail });
      },
      error: (erreur: unknown) => {
        this.selection.set(null);
        this.erreur.set({ idIntervention: intervention.idIntervention, message: messageErreur(erreur) });
      },
    });
  }

  abandonner(): void {
    this.selection.set(null);
    this.erreur.set(null);
  }

  /** Second clic du démarrage. */
  confirmerDemarrage(): void {
    const detail = this.selection()?.detail;
    if (!detail) {
      return;
    }
    this.envoyer(
      this.interventionsApi.demarrerIntervention({
        idIntervention: detail.idIntervention,
        versionRequest: { version: detail.version },
      }),
      `${detail.numero} démarrée.`,
    );
  }

  /** Second clic de la clôture. */
  validerCloture(): void {
    const detail = this.selection()?.detail;
    if (!detail) {
      return;
    }
    if (this.cloture.invalid) {
      this.cloture.markAllAsTouched();
      this.tentatives.update((n) => n + 1);
      return;
    }
    const saisie = this.cloture.getRawValue();
    this.envoyer(
      this.interventionsApi.cloturerIntervention({
        idIntervention: detail.idIntervention,
        clotureRequest: {
          version: detail.version,
          compteRendu: saisie.compteRendu.trim(),
          tempsPasseMin: saisie.tempsPasseMin!,
        },
      }),
      `${detail.numero} clôturée.`,
    );
  }

  protected choisirDuree(minutes: number): void {
    this.cloture.controls.tempsPasseMin.setValue(minutes);
  }

  protected estSelectionnee(intervention: InterventionResume, action: Selection['action']): boolean {
    const selection = this.selection();
    return selection?.idIntervention === intervention.idIntervention && selection.action === action;
  }

  protected messageChamp(champ: 'compteRendu' | 'tempsPasseMin'): string | null {
    this.tentatives();
    const controle = this.cloture.controls[champ];
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (controle.hasError('required')) {
      return champ === 'compteRendu' ? 'Le compte rendu est obligatoire.' : 'Indiquez le temps passé.';
    }
    if (controle.hasError('minlength')) {
      return '10 caractères minimum.';
    }
    if (controle.hasError('min')) {
      return 'Au moins 1 minute.';
    }
    return 'Valeur invalide.';
  }

  /**
   * Le type est écrit explicitement : les méthodes du client généré ont plusieurs surcharges
   * (body, response, events), et ReturnType ne retiendrait que la dernière.
   */
  private envoyer(appel: Observable<InterventionDetail>, message: string): void {
    this.envoiEnCours.set(true);
    appel.pipe(finalize(() => this.envoiEnCours.set(false))).subscribe({
      next: () => {
        this.selection.set(null);
        this.succes.set(message);
        this.rechargement.update((n) => n + 1);
      },
      // L'action est refermée : les autres cartes redeviennent utilisables, le message reste sur la carte.
      // Un nouveau clic relira le détail, donc la version à jour (utile après un conflit de version).
      error: (erreur: unknown) => {
        const idIntervention = this.selection()?.idIntervention ?? null;
        this.selection.set(null);
        this.erreur.set({ idIntervention, message: messageErreur(erreur) });
      },
    });
  }
}

/** Minutes écoulées depuis le démarrage : la proposition par défaut du temps passé. */
function minutesDepuis(dateDebut?: string): number | null {
  if (!dateDebut) {
    return null;
  }
  const minutes = Math.round((Date.now() - new Date(dateDebut).getTime()) / 60_000);
  return Math.max(1, minutes);
}
