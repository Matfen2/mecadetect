import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';

import {
  InterventionsService,
  PageIntervention,
  Priorite,
  StatutIntervention,
  Technicien,
  TechniciensService,
} from '../../core/api';
import { SessionService } from '../../core/auth/session.service';
import { OPTIONS_PRIORITE, OPTIONS_STATUT_INTERVENTION } from '../../shared/libelles-intervention';
import { PrioriteBadge } from '../../shared/priorite-badge';
import { StatutInterventionBadge } from '../../shared/statut-intervention-badge';

/** Critères de la recherche : chaque changement relance un appel à l'API. */
interface Criteres {
  page: number;
  taille: number;
  tri: string;
  statut: string | null;
  priorite: string | null;
  idTechnicien: number | null;
  /** Dates au format AAAA-MM-JJ, bornes incluses */
  depuis: string | null;
  jusqua: string | null;
}

type EtatListe =
  | { statut: 'chargement' }
  | { statut: 'succes'; page: PageIntervention }
  | { statut: 'erreur' };

const CRITERES_INITIAUX: Criteres = {
  page: 0,
  taille: 20,
  tri: 'dateDemande,desc',
  statut: null,
  priorite: null,
  idTechnicien: null,
  depuis: null,
  jusqua: null,
};

/** Liste des interventions, visible par tous les rôles, filtrable et paginée côté serveur. */
@Component({
  selector: 'app-liste-interventions',
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    PrioriteBadge,
    StatutInterventionBadge,
  ],
  templateUrl: './liste-interventions.html',
  styleUrl: './liste-interventions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListeInterventions {
  private readonly interventionsApi = inject(InterventionsService);
  private readonly techniciensApi = inject(TechniciensService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  protected readonly colonnes = ['numero', 'titre', 'equipement', 'priorite', 'statut', 'dateDemande', 'technicien'];
  protected readonly statuts = OPTIONS_STATUT_INTERVENTION;
  protected readonly priorites = OPTIONS_PRIORITE;

  protected readonly estAdmin = computed(() => this.session.role() === 'ADMIN');
  /** Le technicien déclare peu : seuls le demandeur et l'admin voient le bouton (droits de l'API). */
  protected readonly peutDeclarer = computed(() => ['DEMANDEUR', 'ADMIN'].includes(this.session.role() ?? ''));

  /** Filtre par technicien : la liste des techniciens n'est accessible qu'à l'admin. */
  protected readonly techniciens = toSignal(
    toObservable(this.estAdmin).pipe(
      switchMap((admin) =>
        admin ? this.techniciensApi.listerTechniciens({}).pipe(catchError(() => of<Technicien[]>([]))) : of([]),
      ),
    ),
    { initialValue: [] as Technicien[] },
  );

  readonly criteres = signal<Criteres>(CRITERES_INITIAUX);

  /** switchMap annule la requête précédente : jamais de résultats d'une ancienne recherche. */
  protected readonly etat = toSignal(
    toObservable(this.criteres).pipe(
      switchMap((c) =>
        this.interventionsApi
          .rechercherInterventions({
            page: c.page,
            size: c.taille,
            sort: c.tri,
            statut: (c.statut ?? undefined) as StatutIntervention | undefined,
            priorite: (c.priorite ?? undefined) as Priorite | undefined,
            idTechnicien: c.idTechnicien ?? undefined,
            depuis: c.depuis ?? undefined,
            jusqua: c.jusqua ?? undefined,
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

  /** Change un ou plusieurs critères ; tout changement de filtre revient à la première page. */
  modifier(changements: Partial<Criteres>): void {
    this.criteres.update((actuels) => ({ ...actuels, ...changements }));
  }

  /** Champ date vidé : la valeur reçue est une chaîne vide, transformée en absence de filtre. */
  protected modifierDate(champ: 'depuis' | 'jusqua', valeur: string): void {
    this.modifier({ [champ]: valeur || null, page: 0 });
  }

  protected changerTri(tri: Sort): void {
    const valeur = tri.direction ? `${tri.active},${tri.direction}` : CRITERES_INITIAUX.tri;
    this.modifier({ tri: valeur, page: 0 });
  }

  protected changerPage(evenement: PageEvent): void {
    this.modifier({ page: evenement.pageIndex, taille: evenement.pageSize });
  }

  protected reinitialiser(): void {
    this.criteres.set(CRITERES_INITIAUX);
  }

  protected ouvrir(idIntervention: number): void {
    void this.router.navigate(['/interventions', idIntervention]);
  }

  protected declarer(): void {
    void this.router.navigate(['/interventions/declarer']);
  }
}
