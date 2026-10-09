import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { Equipement, EquipementsService } from '../../core/api';
import { SessionService } from '../../core/auth/session.service';
import { StatutEquipementBadge } from '../../shared/statut-equipement-badge';
import { messageErreur } from '../../shared/probleme-api';

type EtatFiche =
  | { statut: 'chargement' }
  | { statut: 'succes'; equipement: Equipement }
  | { statut: 'introuvable' }
  | { statut: 'erreur' };

/** Fiche d'un équipement : consultation pour tous, modification et archivage pour l'admin. */
@Component({
  selector: 'app-fiche-equipement',
  imports: [DatePipe, RouterLink, MatButtonModule, MatProgressBarModule, StatutEquipementBadge],
  templateUrl: './fiche-equipement.html',
  styleUrl: './fiche-equipement.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FicheEquipement {
  private readonly equipementsApi = inject(EquipementsService);
  private readonly session = inject(SessionService);

  /** Paramètre :id de la route, transmis grâce à withComponentInputBinding. */
  readonly id = input.required<string>();

  protected readonly estAdmin = computed(() => this.session.role() === 'ADMIN');

  /** Incrémenté pour recharger la fiche, par exemple après un archivage. */
  private readonly version = signal(0);

  protected readonly etat = toSignal(
    toObservable(computed(() => ({ id: Number(this.id()), version: this.version() }))).pipe(
      switchMap(({ id }) =>
        Number.isInteger(id)
          ? this.equipementsApi.obtenirEquipement({ idEquipement: id }).pipe(
              map((equipement): EtatFiche => ({ statut: 'succes', equipement })),
              catchError((erreur: unknown) =>
                of<EtatFiche>({
                  statut: erreur instanceof HttpErrorResponse && erreur.status === 404 ? 'introuvable' : 'erreur',
                }),
              ),
              startWith<EtatFiche>({ statut: 'chargement' }),
            )
          : of<EtatFiche>({ statut: 'introuvable' }),
      ),
    ),
    { initialValue: { statut: 'chargement' } as EtatFiche },
  );

  protected readonly equipement = computed(() => {
    const etat = this.etat();
    return etat.statut === 'succes' ? etat.equipement : null;
  });

  protected readonly estArchive = computed(() => (this.equipement()?.statut as string | undefined) === 'ARCHIVE');

  protected readonly confirmationArchivage = signal(false);
  protected readonly archivageEnCours = signal(false);
  protected readonly erreurArchivage = signal<string | null>(null);

  protected demanderArchivage(): void {
    this.erreurArchivage.set(null);
    this.confirmationArchivage.set(true);
  }

  protected annulerArchivage(): void {
    this.confirmationArchivage.set(false);
  }

  protected confirmerArchivage(): void {
    const equipement = this.equipement();
    if (!equipement) {
      return;
    }
    this.archivageEnCours.set(true);
    this.equipementsApi.archiverEquipement({ idEquipement: equipement.idEquipement }).subscribe({
      next: () => {
        this.archivageEnCours.set(false);
        this.confirmationArchivage.set(false);
        this.version.update((v) => v + 1);
      },
      error: (erreur: unknown) => {
        this.archivageEnCours.set(false);
        this.confirmationArchivage.set(false);
        this.erreurArchivage.set(messageErreur(erreur));
      },
    });
  }
}
