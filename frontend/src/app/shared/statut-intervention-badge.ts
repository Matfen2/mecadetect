import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { LIBELLES_STATUT_INTERVENTION } from './libelles-intervention';

/** Couleur du voyant selon l'étape du cycle de vie (voir le code des signaux dans styles.scss). */
const SIGNAUX: Record<string, string> = {
  DEMANDEE: 'gris',
  PLANIFIEE: 'bleu',
  EN_COURS: 'ambre',
  TERMINEE: 'vert',
  ANNULEE: 'gris',
};

/** Statut d'une intervention comme un voyant : libellé lisible et couleur selon l'étape. */
@Component({
  selector: 'app-statut-intervention-badge',
  template: `<span [class]="classe()">{{ libelle() }}</span>`,
  styleUrl: './voyant.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatutInterventionBadge {
  readonly statut = input.required<string>();

  protected readonly libelle = computed(() => LIBELLES_STATUT_INTERVENTION[this.statut()] ?? this.statut());
  protected readonly classe = computed(
    () => `badge ${this.statut().toLowerCase().replace('_', '-')} ${SIGNAUX[this.statut()] ?? 'gris'}`,
  );
}
