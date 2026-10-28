import { TestBed } from '@angular/core/testing';

import { PrioriteBadge } from './priorite-badge';
import { StatutInterventionBadge } from './statut-intervention-badge';

describe('Badges d’intervention', () => {
  it('affiche le libellé et la classe du statut', () => {
    const fixture = TestBed.createComponent(StatutInterventionBadge);
    fixture.componentRef.setInput('statut', 'EN_COURS');
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector('span')!;
    expect(badge.textContent).toBe('En cours');
    expect(badge.classList).toContain('en-cours');
  });

  it('affiche le libellé et la classe de la priorité', () => {
    const fixture = TestBed.createComponent(PrioriteBadge);
    fixture.componentRef.setInput('priorite', 'CRITIQUE');
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector('span')!;
    expect(badge.textContent).toBe('Critique');
    expect(badge.classList).toContain('critique');
  });
});
