import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Page provisoire, remplacée par les vrais écrans aux sprints suivants. */
@Component({
  selector: 'app-accueil',
  template: `
    <h1>{{ titre() }}</h1>
    <p>Cet écran sera développé dans un prochain sprint.</p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accueil {
  /** Renseigné par data.titre de la route */
  readonly titre = input<string>('');
}
