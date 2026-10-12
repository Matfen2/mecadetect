import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { LIBELLES_PRIORITE } from './libelles-intervention';

/** Priorité d'une intervention : les priorités hautes et critiques ressortent visuellement. */
@Component({
  selector: 'app-priorite-badge',
  template: `<span [class]="classe()">{{ libelle() }}</span>`,
  styles: `
    .priorite {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      font-size: 0.85rem;
      white-space: nowrap;
    }
    .priorite::before {
      content: '';
      width: 0.55rem;
      height: 0.55rem;
      border-radius: 50%;
      background: currentColor;
    }
    .basse { color: #5f6b7a; }
    .normale { color: #1f4aa8; }
    .haute { color: #b45309; font-weight: 500; }
    .critique { color: #b91c1c; font-weight: 600; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrioriteBadge {
  readonly priorite = input.required<string>();

  protected readonly libelle = computed(() => LIBELLES_PRIORITE[this.priorite()] ?? this.priorite());
  protected readonly classe = computed(() => 'priorite ' + this.priorite().toLowerCase());
}
