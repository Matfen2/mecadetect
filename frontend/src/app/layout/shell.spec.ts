import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { SessionService } from '../core/auth/session.service';
import { Shell } from './shell';

/** Shell avec une session simulée pour le rôle donné. */
function creer(role: string) {
  const deconnecter = vi.fn();
  TestBed.configureTestingModule({
    imports: [Shell],
    providers: [
      provideRouter([]),
      {
        provide: SessionService,
        useValue: {
          role: signal(role),
          utilisateur: signal({ prenom: 'Claire', nom: 'Martin' }),
          deconnecter,
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(Shell);
  fixture.detectChanges();
  return { fixture, deconnecter };
}

/** Textes visibles, espaces normalisés. */
const textes = (elements: Iterable<Element>): string[] =>
  Array.from(elements, (element) => (element.textContent ?? '').replace(/\s+/g, ' ').trim());

/** Libellé d'un élément de menu, sans le nom de son icône. */
const libelle = (element: Element): string =>
  textes([element.querySelector('.mat-mdc-menu-item-text') ?? element])[0];

const boutonMenu = (fixture: ComponentFixture<Shell>): HTMLButtonElement =>
  fixture.nativeElement.querySelector('button.bouton-menu');

/** Ouvre le menu des petits écrans ; ses éléments sont rendus dans la surcouche de Material. */
async function ouvrirMenu(fixture: ComponentFixture<Shell>): Promise<HTMLElement[]> {
  boutonMenu(fixture).click();
  fixture.detectChanges();
  await fixture.whenStable();
  return Array.from(document.querySelectorAll<HTMLElement>('.mat-mdc-menu-panel .mat-mdc-menu-item'));
}

describe('Shell', () => {
  it('affiche dans la barre les liens du rôle connecté', () => {
    const { fixture } = creer('ADMIN');

    const liens = fixture.nativeElement.querySelectorAll('nav a');

    expect(textes(liens)).toEqual(['Tableau de bord', 'Interventions', 'Équipements']);
  });

  it('propose un bouton menu accessible pour les petits écrans', () => {
    const { fixture } = creer('ADMIN');

    const bouton = boutonMenu(fixture);

    expect(bouton).toBeTruthy();
    expect(bouton.getAttribute('aria-label')).toBe('Ouvrir le menu');
    expect(bouton.getAttribute('aria-haspopup')).toBe('menu');
  });

  it('le menu reprend les liens du rôle, puis la déconnexion', async () => {
    const { fixture } = creer('TECHNICIEN');

    const elements = await ouvrirMenu(fixture);

    expect(elements.map(libelle)).toEqual(['Mes interventions', 'Interventions', 'Équipements', 'Déconnexion']);
  });

  it("le menu affiche l'utilisateur connecté", async () => {
    const { fixture } = creer('DEMANDEUR');

    await ouvrirMenu(fixture);

    expect(document.querySelector('.mat-mdc-menu-panel .menu-utilisateur')?.textContent?.trim()).toBe(
      'Claire Martin',
    );
  });

  it('la déconnexion depuis le menu ferme la session', async () => {
    const { fixture, deconnecter } = creer('DEMANDEUR');

    const elements = await ouvrirMenu(fixture);
    elements.at(-1)!.click();

    expect(deconnecter).toHaveBeenCalledOnce();
  });
});
