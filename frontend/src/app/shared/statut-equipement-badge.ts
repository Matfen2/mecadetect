import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const LIBELLES: Record<string, string> = {
  EN_SERVICE: 'En service',
  EN_PANNE: 'En panne',
  EN_MAINTENANCE: 'En maintenance',
  ARCHIVE: 'Archivé',
};

/** Affiche le statut d'un équipement avec un libellé lisible et une couleur. */
@Component({
  selector: 'app-statut-equipement-badge',
  template: `<span class="badge" [class]="classe()">{{ libelle() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.15rem 0.6rem;
      border-radius: 999px;
      font-size: 0.8rem;
      font-weight: 500;
      white-space: nowrap;
    }
    .en-service { background: #e3f4e8; color: #1b6b34; }
    .en-panne { background: #fde7e7; color: #a32020; }
    .en-maintenance { background: #fff2d6; color: #8a5a00; }
    .archive { background: #ececec; color: #555; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatutEquipementBadge {
  readonly statut = input.required<string>();

  protected readonly libelle = computed(() => LIBELLES[this.statut()] ?? this.statut());
  protected readonly classe = computed(() => 'badge ' + this.statut().toLowerCase().replace('_', '-'));
}
