import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { Dashboard, DashboardService } from '../../core/api';
import { LIBELLES_PRIORITE } from '../../shared/libelles-intervention';
import { messageErreur } from '../../shared/probleme-api';
import { ExportInterventions } from './export-interventions';
import {
  duree,
  estMoisEnCours,
  heures,
  libelleMois,
  libelleMoisCourt,
  nombre,
  proportion,
  variation,
} from './indicateurs';

type Etat =
  | { statut: 'chargement' }
  | { statut: 'succes'; tableau: Dashboard }
  | { statut: 'erreur'; message: string };

/**
 * Tableau de bord du responsable de maintenance (tâche 5.2).
 * Un seul appel à l'API (GET /dashboard, tâche 5.1) alimente tous les blocs ; l'export CSV (tâche 5.3)
 * est proposé dans l'en-tête.
 *
 * Les graphiques sont de simples barres en HTML/CSS : une seule série par graphique, donc une seule
 * couleur ; la valeur exacte s'affiche au survol ou au focus, et chaque graphique a sa version tableau
 * pour les lecteurs d'écran.
 */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [MatButtonModule, MatProgressBarModule, ExportInterventions],
  templateUrl: './tableau-de-bord.html',
  styleUrl: './tableau-de-bord.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableauDeBord {
  private readonly dashboardApi = inject(DashboardService);

  protected readonly etat = signal<Etat>({ statut: 'chargement' });

  /** Données prêtes à afficher, ou null tant que le chargement n'a pas réussi. */
  protected readonly vue = computed(() => {
    const etat = this.etat();
    return etat.statut === 'succes' ? TableauDeBord.preparer(etat.tableau) : null;
  });

  protected readonly erreur = computed(() => {
    const etat = this.etat();
    return etat.statut === 'erreur' ? etat.message : null;
  });

  protected readonly nombre = nombre;
  protected readonly duree = duree;
  protected readonly heures = heures;
  protected readonly variation = variation;

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.etat.set({ statut: 'chargement' });
    this.dashboardApi.obtenirDashboard().subscribe({
      next: (tableau) => this.etat.set({ statut: 'succes', tableau }),
      error: (erreur: unknown) => this.etat.set({ statut: 'erreur', message: messageErreur(erreur) }),
    });
  }

  private static preparer(tableau: Dashboard) {
    const maxPriorite = Math.max(0, ...tableau.ouvertesParPriorite.map((p) => p.nombre));
    const maxPannes = Math.max(0, ...tableau.topEquipements.map((e) => e.nombrePannes));
    const maxMois = Math.max(0, ...tableau.evolutionMensuelle.map((m) => m.nombre));
    const maxTerminees = Math.max(0, ...tableau.chargeTechniciens.map((c) => c.interventionsMois));

    return {
      totalOuvertes: tableau.ouvertesParPriorite.reduce((total, p) => total + p.nombre, 0),
      equipementsEnPanne: tableau.equipementsEnPanne,
      tempsMoyen: tableau.tempsMoyenResolutionHeures,
      priorites: tableau.ouvertesParPriorite.map((p) => ({
        code: p.priorite as string,
        libelle: LIBELLES_PRIORITE[p.priorite] ?? p.priorite,
        nombre: p.nombre,
        largeur: proportion(p.nombre, maxPriorite),
      })),
      top: tableau.topEquipements.map((e) => ({ ...e, largeur: proportion(e.nombrePannes, maxPannes) })),
      mois: tableau.evolutionMensuelle.map((m) => ({
        ...m,
        libelle: libelleMois(m.mois),
        libelleCourt: libelleMoisCourt(m.mois),
        hauteur: proportion(m.nombre, maxMois),
        enCours: estMoisEnCours(m.mois),
      })),
      // Classés par interventions terminées ce mois-ci, la grandeur affichée
      techniciens: [...tableau.chargeTechniciens]
        .sort((a, b) => b.interventionsMois - a.interventionsMois)
        .map((c) => ({ ...c, largeur: proportion(c.interventionsMois, maxTerminees) })),
    };
  }
}
