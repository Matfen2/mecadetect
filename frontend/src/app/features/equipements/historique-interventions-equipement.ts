import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { EquipementsService, PageIntervention } from '../../core/api';
import { StatutInterventionBadge } from '../../shared/statut-intervention-badge';

type EtatHistorique =
  | { statut: 'chargement' }
  | { statut: 'succes'; page: PageIntervention }
  | { statut: 'erreur' };

/** Interventions d'un équipement, les plus récentes d'abord (fiche équipement). */
@Component({
  selector: 'app-historique-interventions-equipement',
  imports: [DatePipe, RouterLink, MatPaginatorModule, MatProgressBarModule, StatutInterventionBadge],
  template: `
    @switch (etat().statut) {
      @case ('chargement') {
        <mat-progress-bar mode="indeterminate" />
      }
      @case ('erreur') {
        <p class="message erreur" role="alert">Impossible de charger l'historique des interventions.</p>
      }
    }

    @if (etat(); as e) {
      @if (e.statut === 'succes') {
        @if (e.page.totalElements === 0) {
          <p class="message">Aucune intervention sur cet équipement.</p>
        } @else {
          <ul class="historique">
            @for (intervention of e.page.contenu; track intervention.idIntervention) {
              <li>
                <a [routerLink]="['/interventions', intervention.idIntervention]">
                  <span class="numero">{{ intervention.numero }}</span>
                  <span class="titre">{{ intervention.titre }}</span>
                </a>
                <span class="date">{{ intervention.dateDemande | date: 'dd/MM/yyyy' }}</span>
                <app-statut-intervention-badge [statut]="intervention.statut" />
              </li>
            }
          </ul>
          @if (e.page.totalPages > 1) {
            <mat-paginator [length]="e.page.totalElements" [pageIndex]="e.page.page" [pageSize]="e.page.taille"
                           [hidePageSize]="true" (page)="changerPage($event)"
                           aria-label="Pagination de l'historique" />
          }
        }
      }
    }
  `,
  styles: `
    .historique {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      display: grid;
      grid-template-columns: 1fr auto auto;
      align-items: center;
      gap: 1rem;
      padding: 0.6rem 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    a {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.75rem;
      color: inherit;
    }
    .numero {
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .date {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.9rem;
    }
    .message {
      color: var(--mat-sys-on-surface-variant);
    }
    .message.erreur {
      color: var(--mat-sys-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoriqueInterventionsEquipement {
  private readonly equipementsApi = inject(EquipementsService);

  readonly idEquipement = input.required<number>();

  private readonly page = signal(0);

  protected readonly etat = toSignal(
    toObservable(computed(() => ({ id: this.idEquipement(), page: this.page() }))).pipe(
      switchMap(({ id, page }) =>
        this.equipementsApi.historiqueInterventionsEquipement({ idEquipement: id, page, size: 10 }).pipe(
          map((resultat): EtatHistorique => ({ statut: 'succes', page: resultat })),
          catchError(() => of<EtatHistorique>({ statut: 'erreur' })),
          startWith<EtatHistorique>({ statut: 'chargement' }),
        ),
      ),
    ),
    { initialValue: { statut: 'chargement' } as EtatHistorique },
  );

  protected changerPage(evenement: PageEvent): void {
    this.page.set(evenement.pageIndex);
  }
}
