import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, finalize, map, of, startWith, switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { InterventionDetail, InterventionsService, Technicien, TechniciensService } from '../../core/api';
import { SessionService } from '../../core/auth/session.service';
import { LIBELLES_STATUT_INTERVENTION } from '../../shared/libelles-intervention';
import { PrioriteBadge } from '../../shared/priorite-badge';
import { lireProbleme, messageErreur } from '../../shared/probleme-api';
import { StatutInterventionBadge } from '../../shared/statut-intervention-badge';
import { ActionIntervention, actionsPossibles } from './actions-intervention';

type EtatDetail =
  | { statut: 'chargement' }
  | { statut: 'succes'; intervention: InterventionDetail }
  | { statut: 'introuvable' }
  | { statut: 'erreur' };

/** Actions qui demandent une saisie avant l'envoi ; « démarrer » part directement. */
type ActionAvecSaisie = Exclude<ActionIntervention, 'demarrer'>;

/**
 * Détail d'une intervention : informations, historique des statuts
 * et boutons d'action selon le rôle et le statut (tâche 3.9).
 * Chaque action renvoie l'intervention à jour : la page l'affiche sans recharger.
 */
@Component({
  selector: 'app-detail-intervention',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    PrioriteBadge,
    StatutInterventionBadge,
  ],
  templateUrl: './detail-intervention.html',
  styleUrl: './detail-intervention.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailIntervention {
  private readonly interventionsApi = inject(InterventionsService);
  private readonly techniciensApi = inject(TechniciensService);
  private readonly session = inject(SessionService);

  /** Paramètre :id de la route, transmis grâce à withComponentInputBinding. */
  readonly id = input.required<string>();

  protected readonly libellesStatut = LIBELLES_STATUT_INTERVENTION;

  /** Incrémenté pour relire l'intervention depuis le serveur (après un conflit de version). */
  private readonly rechargement = signal(0);

  protected readonly etat = signal<EtatDetail>({ statut: 'chargement' });

  protected readonly intervention = computed(() => {
    const etat = this.etat();
    return etat.statut === 'succes' ? etat.intervention : null;
  });

  protected readonly actions = computed(() => {
    const intervention = this.intervention();
    if (!intervention) {
      return [];
    }
    return actionsPossibles(intervention, {
      role: this.session.role(),
      idUtilisateur: this.session.utilisateur()?.idUtilisateur ?? null,
    });
  });

  // ------------------------------------------------------------------ état des actions
  protected readonly actionOuverte = signal<ActionAvecSaisie | null>(null);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurAction = signal<string | null>(null);
  /** Quelqu'un a modifié l'intervention entre-temps (409 CONFLIT_VERSION, UC-DB6). */
  protected readonly conflit = signal(false);
  protected readonly techniciens = signal<Technicien[] | null>(null);
  /** Nombre d'envois refusés par la validation, lu par erreurChamp pour rafraîchir les messages. */
  private readonly tentatives = signal(0);

  readonly planification = new FormGroup({
    idTechnicien: new FormControl<number | null>(null, { validators: [Validators.required] }),
    /** Valeur d'un champ datetime-local : heure locale, sans fuseau (AAAA-MM-JJTHH:mm) */
    datePlanifiee: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly cloture = new FormGroup({
    compteRendu: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10), Validators.maxLength(2000)],
    }),
    tempsPasseMin: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1), Validators.max(999999)],
    }),
  });

  readonly annulation = new FormGroup({
    motif: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(5), Validators.maxLength(500)],
    }),
  });

  constructor() {
    toObservable(computed(() => ({ id: Number(this.id()), rechargement: this.rechargement() })))
      .pipe(
        switchMap(({ id }) =>
          Number.isInteger(id)
            ? this.interventionsApi.obtenirIntervention({ idIntervention: id }).pipe(
                map((intervention): EtatDetail => ({ statut: 'succes', intervention })),
                catchError((erreur: unknown) =>
                  of<EtatDetail>({
                    statut: erreur instanceof HttpErrorResponse && erreur.status === 404 ? 'introuvable' : 'erreur',
                  }),
                ),
                startWith<EtatDetail>({ statut: 'chargement' }),
              )
            : of<EtatDetail>({ statut: 'introuvable' }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((etat) => this.etat.set(etat));
  }

  // ------------------------------------------------------------------ ouverture des formulaires
  ouvrir(action: ActionAvecSaisie): void {
    this.erreurAction.set(null);
    this.conflit.set(false);
    this.actionOuverte.set(action);
    if (action === 'planifier') {
      this.planification.reset({ idTechnicien: null, datePlanifiee: prochaineHeurePleine() });
      this.chargerTechniciens();
    }
    if (action === 'cloturer') {
      this.cloture.reset();
    }
    if (action === 'annuler') {
      this.annulation.reset();
    }
  }

  fermer(): void {
    this.actionOuverte.set(null);
    this.erreurAction.set(null);
  }

  recharger(): void {
    this.fermer();
    this.conflit.set(false);
    this.rechargement.update((n) => n + 1);
  }

  // ------------------------------------------------------------------ envoi des actions
  planifier(): void {
    const intervention = this.intervention();
    if (!intervention || !this.valider(this.planification)) {
      return;
    }
    const saisie = this.planification.getRawValue();
    this.executer(
      this.interventionsApi.planifierIntervention({
        idIntervention: intervention.idIntervention,
        planificationRequest: {
          version: intervention.version,
          idTechnicien: saisie.idTechnicien!,
          // L'heure saisie est locale ; l'API attend un instant précis (date-time ISO 8601)
          datePlanifiee: new Date(saisie.datePlanifiee).toISOString(),
        },
      }),
    );
  }

  demarrer(): void {
    const intervention = this.intervention();
    if (!intervention) {
      return;
    }
    this.erreurAction.set(null);
    this.executer(
      this.interventionsApi.demarrerIntervention({
        idIntervention: intervention.idIntervention,
        versionRequest: { version: intervention.version },
      }),
    );
  }

  cloturer(): void {
    const intervention = this.intervention();
    if (!intervention || !this.valider(this.cloture)) {
      return;
    }
    const saisie = this.cloture.getRawValue();
    this.executer(
      this.interventionsApi.cloturerIntervention({
        idIntervention: intervention.idIntervention,
        clotureRequest: {
          version: intervention.version,
          compteRendu: saisie.compteRendu.trim(),
          tempsPasseMin: saisie.tempsPasseMin!,
        },
      }),
    );
  }

  annuler(): void {
    const intervention = this.intervention();
    if (!intervention || !this.valider(this.annulation)) {
      return;
    }
    this.executer(
      this.interventionsApi.annulerIntervention({
        idIntervention: intervention.idIntervention,
        annulationRequest: { version: intervention.version, motif: this.annulation.getRawValue().motif.trim() },
      }),
    );
  }

  // ------------------------------------------------------------------ affichage
  protected nomComplet(utilisateur?: { prenom: string; nom: string } | null): string {
    return utilisateur ? `${utilisateur.prenom} ${utilisateur.nom}` : '—';
  }

  /** « 1 h 30 » plutôt que « 90 min » au-delà d'une heure. */
  protected duree(minutes?: number | null): string {
    if (minutes === undefined || minutes === null) {
      return '—';
    }
    const heures = Math.floor(minutes / 60);
    const reste = minutes % 60;
    if (heures === 0) {
      return `${reste} min`;
    }
    return reste === 0 ? `${heures} h` : `${heures} h ${String(reste).padStart(2, '0')}`;
  }

  protected erreurChamp(controle: AbstractControl): string | null {
    this.tentatives();
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Ce champ est obligatoire.';
    }
    if (controle.hasError('minlength')) {
      return `${controle.getError('minlength').requiredLength} caractères minimum.`;
    }
    if (controle.hasError('maxlength')) {
      return `${controle.getError('maxlength').requiredLength} caractères maximum.`;
    }
    if (controle.hasError('min')) {
      return `La valeur minimale est ${controle.getError('min').min}.`;
    }
    if (controle.hasError('max')) {
      return `La valeur maximale est ${controle.getError('max').max}.`;
    }
    return null;
  }

  // ------------------------------------------------------------------ interne
  private valider(formulaire: FormGroup): boolean {
    this.erreurAction.set(null);
    if (formulaire.invalid) {
      formulaire.markAllAsTouched();
      // markAllAsTouched ne prévient pas le composant OnPush : ce signal le fait
      this.tentatives.update((n) => n + 1);
      return false;
    }
    return true;
  }

  private executer(appel: Observable<InterventionDetail>): void {
    this.envoiEnCours.set(true);
    appel.pipe(finalize(() => this.envoiEnCours.set(false))).subscribe({
      next: (intervention) => {
        this.etat.set({ statut: 'succes', intervention });
        this.actionOuverte.set(null);
      },
      error: (erreur: unknown) => {
        this.conflit.set(lireProbleme(erreur)?.code === 'CONFLIT_VERSION');
        this.erreurAction.set(messageErreur(erreur));
      },
    });
  }

  /** Seuls les techniciens habilités sur le type de l'équipement sont proposés (RG-09). */
  private chargerTechniciens(): void {
    const intervention = this.intervention();
    if (!intervention) {
      return;
    }
    this.techniciens.set(null);
    this.techniciensApi
      .listerTechniciens({ idType: intervention.equipement.type.idType })
      .pipe(catchError(() => of<Technicien[]>([])))
      .subscribe((techniciens) => this.techniciens.set(techniciens));
  }
}

/** Heure pleine suivante, au format attendu par un champ datetime-local. */
function prochaineHeurePleine(): string {
  const date = new Date();
  date.setHours(date.getHours() + 1, 0, 0, 0);
  const deuxChiffres = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${deuxChiffres(date.getMonth() + 1)}-${deuxChiffres(date.getDate())}` +
    `T${deuxChiffres(date.getHours())}:00`
  );
}
