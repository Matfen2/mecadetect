import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { LIBELLES_STATUT_INTERVENTION } from './libelles-intervention';

/** Statut d'une intervention : libellé lisible et couleur selon l'étape du cycle de vie. */
@Component({
  selector: 'app-statut-intervention-badge',
  template: `<span [class]="classe()">{{ libelle() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.15rem 0.6rem;
      border-radius: 999px;
      font-size: 0.8rem;
      font-weight: 500;
      white-space: nowrap;
    }
    .demandee { background: #e8eefc; color: #1f4aa8; }
    .planifiee { background: #efe6fb; color: #5b2a9e; }
    .en-cours { background: #fff2d6; color: #8a5a00; }
    .terminee { background: #e3f4e8; color: #1b6b34; }
    .annulee { background: #ececec; color: #555; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatutInterventionBadge {
  readonly statut = input.required<string>();

  protected readonly libelle = computed(() => LIBELLES_STATUT_INTERVENTION[this.statut()] ?? this.statut());
  protected readonly classe = computed(() => 'badge ' + this.statut().toLowerCase().replace('_', '-'));
}
