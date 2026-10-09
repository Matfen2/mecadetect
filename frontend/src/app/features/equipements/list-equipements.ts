import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';

import {
  EquipementsService,
  PageEquipement,
  ReferentielService,
  StatutEquipement,
} from '../../core/api';
import { SessionService } from '../../core/auth/session.service';
import { StatutEquipementBadge } from '../../shared/statut-equipement-badge';

/** Critères de la recherche : chaque changement relance un appel à l'API. */
interface Criteres {
  page: number;
  taille: number;
  tri: string;
  statut: string | null;
  idType: number | null;
  idZone: number | null;
  recherche: string;
}

type EtatListe =
  | { statut: 'chargement' }
  | { statut: 'succes'; page: PageEquipement }
  | { statut: 'erreur' };

const CRITERES_INITIAUX: Criteres = {
  page: 0,
  taille: 20,
  tri: 'reference,asc',
  statut: null,
  idType: null,
  idZone: null,
  recherche: '',
};

@Component({
  selector: 'app-liste-equipements',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    StatutEquipementBadge,
  ],
  templateUrl: './liste-equipements.html',
  styleUrl: './liste-equipements.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListeEquipements {
  private readonly equipementsApi = inject(EquipementsService);
  private readonly referentielApi = inject(ReferentielService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  protected readonly colonnes = ['reference', 'nom', 'type', 'zone', 'dateMiseService', 'statut'];
  protected readonly statuts = [
    { valeur: 'EN_SERVICE', libelle: 'En service' },
    { valeur: 'EN_PANNE', libelle: 'En panne' },
    { valeur: 'EN_MAINTENANCE', libelle: 'En maintenance' },
    { valeur: 'ARCHIVE', libelle: 'Archivé' },
  ];

  protected readonly estAdmin = computed(() => this.session.role() === 'ADMIN');

  /** Listes déroulantes : chargées une fois depuis le référentiel. */
  protected readonly zones = toSignal(this.referentielApi.listerZones(), { initialValue: [] });
  protected readonly types = toSignal(this.referentielApi.listerTypesEquipement(), { initialValue: [] });

  readonly criteres = signal<Criteres>(CRITERES_INITIAUX);

  /** Champ de recherche : l'API n'est appelée qu'après 300 ms sans frappe. */
  protected readonly champRecherche = new FormControl('', { nonNullable: true });

  /**
   * À chaque changement de critères, un nouvel appel à l'API.
   * switchMap annule la requête précédente si elle n'est pas terminée :
   * pas de résultats d'une ancienne recherche qui arriveraient en retard.
   */
  protected readonly etat = toSignal(
    toObservable(this.criteres).pipe(
      switchMap((c) =>
        this.equipementsApi
          .rechercherEquipements({
            page: c.page,
            size: c.taille,
            sort: c.tri,
            statut: (c.statut ?? undefined) as StatutEquipement | undefined,
            idType: c.idType ?? undefined,
            idZone: c.idZone ?? undefined,
            recherche: c.recherche || undefined,
          })
          .pipe(
            map((page): EtatListe => ({ statut: 'succes', page })),
            catchError(() => of<EtatListe>({ statut: 'erreur' })),
            startWith<EtatListe>({ statut: 'chargement' }),
          ),
      ),
    ),
    { initialValue: { statut: 'chargement' } as EtatListe },
  );

  constructor() {
    this.champRecherche.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((texte) => this.modifier({ recherche: texte.trim(), page: 0 }));
  }

  /** Change un ou plusieurs critères ; tout changement de filtre revient à la première page. */
  modifier(changements: Partial<Criteres>): void {
    this.criteres.update((actuels) => ({ ...actuels, ...changements }));
  }

  protected changerTri(tri: Sort): void {
    const valeur = tri.direction ? `${tri.active},${tri.direction}` : CRITERES_INITIAUX.tri;
    this.modifier({ tri: valeur, page: 0 });
  }

  protected changerPage(evenement: PageEvent): void {
    this.modifier({ page: evenement.pageIndex, taille: evenement.pageSize });
  }

  protected reinitialiser(): void {
    this.champRecherche.setValue('', { emitEvent: false });
    this.criteres.set(CRITERES_INITIAUX);
  }

  protected ouvrir(idEquipement: number): void {
    void this.router.navigate(['/equipements', idEquipement]);
  }

  protected creer(): void {
    void this.router.navigate(['/equipements/nouveau']);
  }
}
