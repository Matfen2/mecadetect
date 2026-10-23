import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { LIBELLES_PRIORITE } from './libelles-intervention';

/** Couleur du voyant selon l'urgence : seule une priorité critique s'allume en rouge. */
const SIGNAUX: Record<string, string> = {
  CRITIQUE: 'rouge',
  HAUTE: 'ambre',
  NORMALE: 'bleu',
  BASSE: 'gris',
};

/** Priorité d'une intervention comme un voyant : libellé lisible et couleur d'urgence. */
@Component({
  selector: 'app-priorite-badge',
  template: `<span [class]="classe()">{{ libelle() }}</span>`,
  styleUrl: './voyant.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrioriteBadge {
  readonly priorite = input.required<string>();

  protected readonly libelle = computed(() => (LIBELLES_PRIORITE as Record<string, string>)[this.priorite()] ?? this.priorite());
  protected readonly classe = computed(
    () => `badge ${this.priorite().toLowerCase()} ${SIGNAUX[this.priorite()] ?? 'gris'}`,
  );
}
