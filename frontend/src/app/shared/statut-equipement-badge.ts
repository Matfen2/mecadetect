import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const LIBELLES: Record<string, string> = {
  EN_SERVICE: 'En service',
  EN_PANNE: 'En panne',
  EN_MAINTENANCE: 'En maintenance',
  ARCHIVE: 'Archivé',
};

/** Couleur du voyant selon le code des signaux (voir styles.scss). */
const SIGNAUX: Record<string, string> = {
  EN_SERVICE: 'vert',
  EN_PANNE: 'rouge',
  EN_MAINTENANCE: 'ambre',
  ARCHIVE: 'gris',
};

/** Affiche le statut d'un équipement comme un voyant : libellé lisible et couleur de signal. */
@Component({
  selector: 'app-statut-equipement-badge',
  template: `<span [class]="classe()">{{ libelle() }}</span>`,
  styleUrl: './voyant.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatutEquipementBadge {
  readonly statut = input.required<string>();

  protected readonly libelle = computed(() => LIBELLES[this.statut()] ?? this.statut());
  protected readonly classe = computed(
    () => `badge ${this.statut().toLowerCase().replace('_', '-')} ${SIGNAUX[this.statut()] ?? 'gris'}`,
  );
}
