import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { ImportsService, RapportImport } from '../../core/api';
import { messageErreur } from '../../shared/probleme-api';
import { Telechargement } from '../../shared/telechargement';

/** Même limite que l'API (spring.servlet.multipart.max-file-size). */
export const TAILLE_MAX_OCTETS = 2 * 1024 * 1024;

/** Ligne d'en-tête attendue par l'API, dans cet ordre. */
export const ENTETE_CSV = 'reference;nom;date_mise_service;type;zone';

/** Modèle proposé au téléchargement : l'en-tête et une ligne d'exemple, avec le BOM pour Excel. */
const MODELE_CSV = `﻿${ENTETE_CSV}\r\nPRS-010;Presse 630 tonnes;2025-03-17;Presse hydraulique;A1\r\n`;

/**
 * Import du parc d'équipements depuis un fichier CSV (admin).
 *
 * Le fichier est vérifié avant l'envoi, avec les mêmes règles que l'API (extension, taille) ;
 * l'API le traite par lots (Spring Batch) et renvoie un rapport : lignes lues, équipements créés,
 * et chaque ligne rejetée avec son numéro et son motif.
 */
@Component({
  selector: 'app-import-equipements',
  imports: [MatButtonModule, MatProgressBarModule, RouterLink],
  templateUrl: './import-equipements.html',
  styleUrl: './import-equipements.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportEquipements {
  private readonly importsApi = inject(ImportsService);
  private readonly telechargement = inject(Telechargement);

  protected readonly entete = ENTETE_CSV;
  protected readonly fichier = signal<File | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly rapport = signal<RapportImport | null>(null);

  /** Au-delà des 100 premiers, l'API compte les rejets sans les détailler. */
  protected readonly rejetsNonDetailles = computed(() => {
    const rapport = this.rapport();
    return rapport !== null && rapport.rejetes > rapport.rejets.length;
  });

  protected choisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0] ?? null;
    this.rapport.set(null);

    const probleme = fichier ? ImportEquipements.verifier(fichier) : null;
    this.erreur.set(probleme);
    this.fichier.set(probleme ? null : fichier);
  }

  protected importer(): void {
    const fichier = this.fichier();
    if (!fichier || this.enCours()) {
      return;
    }

    this.enCours.set(true);
    this.erreur.set(null);
    this.rapport.set(null);
    this.importsApi.importerEquipements({ fichier }).subscribe({
      next: (rapport) => {
        this.rapport.set(rapport);
        this.enCours.set(false);
      },
      error: (erreur: unknown) => {
        this.erreur.set(messageErreur(erreur));
        this.enCours.set(false);
      },
    });
  }

  protected telechargerModele(): void {
    this.telechargement.enregistrer(new Blob([MODELE_CSV], { type: 'text/csv' }), 'modele-import-equipements.csv');
  }

  private static verifier(fichier: File): string | null {
    if (!fichier.name.toLowerCase().endsWith('.csv')) {
      return 'Choisissez un fichier .csv (dans Excel : « Enregistrer sous », format CSV UTF-8).';
    }
    if (fichier.size > TAILLE_MAX_OCTETS) {
      return 'Le fichier dépasse la taille maximale autorisée (2 Mo).';
    }
    return null;
  }
}
