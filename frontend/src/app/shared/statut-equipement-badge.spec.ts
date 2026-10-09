import { TestBed } from '@angular/core/testing';

import { StatutEquipementBadge } from './statut-equipement-badge';

describe('StatutEquipementBadge', () => {
  function afficher(statut: string): HTMLElement {
    const fixture = TestBed.createComponent(StatutEquipementBadge);
    fixture.componentRef.setInput('statut', statut);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('.badge')!;
  }

  it('affiche un libellé lisible et la classe de couleur', () => {
    const badge = afficher('EN_PANNE');

    expect(badge.textContent?.trim()).toBe('En panne');
    expect(badge.classList).toContain('en-panne');
  });

  it('gère les statuts en deux mots', () => {
    expect(afficher('EN_MAINTENANCE').classList).toContain('en-maintenance');
  });

  it('affiche la valeur brute pour un statut inconnu', () => {
    expect(afficher('INCONNU').textContent?.trim()).toBe('INCONNU');
  });
});
