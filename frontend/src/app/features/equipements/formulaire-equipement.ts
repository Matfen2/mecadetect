import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { filter, finalize, switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { Equipement, EquipementRequest, EquipementsService, ReferentielService } from '../../core/api';
import { lireProbleme, messageErreur } from '../../shared/probleme-api';

type ChampEquipement = 'reference' | 'nom' | 'dateMiseService' | 'idType' | 'idZone';

/** Codes métier de l'API rattachés à un champ précis du formulaire. */
const CHAMP_PAR_CODE: Record<string, ChampEquipement> = {
  REFERENCE_DEJA_UTILISEE: 'reference',
  TYPE_INCONNU: 'idType',
  ZONE_INCONNUE: 'idZone',
};

type EtatChargement = 'pret' | 'chargement' | 'introuvable' | 'erreur';

/**
 * Formulaire de création (route /equipements/nouveau)
 * et de modification (route /equipements/:id/modifier) d'un équipement.
 */
@Component({
  selector: 'app-formulaire-equipement',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './formulaire-equipement.html',
  styleUrl: './formulaire-equipement.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormulaireEquipement {
  private readonly equipementsApi = inject(EquipementsService);
  private readonly referentielApi = inject(ReferentielService);
  private readonly router = inject(Router);

  /** Paramètre :id de la route : absent en création, présent en modification. */
  readonly id = input<string>();

  protected readonly modeModification = computed(() => this.id() !== undefined);
  protected readonly zones = toSignal(this.referentielApi.listerZones(), { initialValue: [] });
  protected readonly types = toSignal(this.referentielApi.listerTypesEquipement(), { initialValue: [] });

  readonly formulaire = new FormGroup({
    reference: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(30)] }),
    nom: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    dateMiseService: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    idType: new FormControl<number | null>(null, { validators: [Validators.required] }),
    idZone: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  protected readonly etatChargement = signal<EtatChargement>('pret');
  protected readonly estArchive = signal(false);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurGlobale = signal<string | null>(null);
  /** Messages d'erreur renvoyés par l'API, par champ. */
  protected readonly erreursServeur = signal<Partial<Record<ChampEquipement, string>>>({});

  constructor() {
    // En modification : chargement de l'équipement pour pré-remplir le formulaire
    toObservable(this.id)
      .pipe(
        filter((id): id is string => id !== undefined),
        switchMap((id) => {
          this.etatChargement.set('chargement');
          return this.equipementsApi.obtenirEquipement({ idEquipement: Number(id) });
        }),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (equipement) => this.preremplir(equipement),
        error: (erreur: unknown) =>
          this.etatChargement.set(erreur instanceof HttpErrorResponse && erreur.status === 404 ? 'introuvable' : 'erreur'),
      });
  }

  enregistrer(): void {
    this.erreurGlobale.set(null);
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      // Signal mis à jour pour rafraîchir l'affichage des erreurs
      this.erreursServeur.set({});
      return;
    }

    const saisie = this.formulaire.getRawValue();
    const requete: EquipementRequest = {
      reference: saisie.reference.trim(),
      nom: saisie.nom.trim(),
      dateMiseService: saisie.dateMiseService,
      idType: saisie.idType!,
      idZone: saisie.idZone!,
    };

    const id = this.id();
    const appel =
      id === undefined
        ? this.equipementsApi.creerEquipement({ equipementRequest: requete })
        : this.equipementsApi.modifierEquipement({ idEquipement: Number(id), equipementRequest: requete });

    this.envoiEnCours.set(true);
    appel.pipe(finalize(() => this.envoiEnCours.set(false))).subscribe({
      next: (equipement) => void this.router.navigate(['/equipements', equipement.idEquipement]),
      error: (erreur: unknown) => this.afficherErreur(erreur),
    });
  }

  /** Lien d'annulation : retour à la fiche en modification, à la liste en création. */
  protected readonly lienRetour = computed(() => {
    const id = this.id();
    return id === undefined ? ['/equipements'] : ['/equipements', id];
  });

  protected erreur(champ: ChampEquipement): string | null {
    const controle = this.formulaire.controls[champ];
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (controle.hasError('serveur')) {
      return this.erreursServeur()[champ] ?? null;
    }
    if (controle.hasError('required')) {
      return MESSAGES_OBLIGATOIRES[champ];
    }
    if (controle.hasError('maxlength')) {
      return `${controle.getError('maxlength').requiredLength} caractères maximum.`;
    }
    return null;
  }

  private preremplir(equipement: Equipement): void {
    this.formulaire.setValue({
      reference: equipement.reference,
      nom: equipement.nom,
      dateMiseService: equipement.dateMiseService,
      idType: equipement.type.idType,
      idZone: equipement.zone.idZone,
    });
    const archive = (equipement.statut as string) === 'ARCHIVE';
    this.estArchive.set(archive);
    if (archive) {
      this.formulaire.disable();
    }
    this.etatChargement.set('pret');
  }

  /**
   * Affiche les erreurs de l'API au plus près de leur cause :
   * sous le champ concerné quand c'est possible, en haut du formulaire sinon.
   */
  private afficherErreur(erreur: unknown): void {
    const probleme = lireProbleme(erreur);
    const messages: Partial<Record<ChampEquipement, string>> = {};

    for (const erreurChamp of probleme?.erreurs ?? []) {
      if (erreurChamp.champ in this.formulaire.controls) {
        messages[erreurChamp.champ as ChampEquipement] = erreurChamp.message;
      }
    }
    const champDuCode = probleme?.code ? CHAMP_PAR_CODE[probleme.code] : undefined;
    if (champDuCode && probleme?.detail) {
      messages[champDuCode] = probleme.detail;
    }

    const champs = Object.keys(messages) as ChampEquipement[];
    for (const champ of champs) {
      // L'erreur disparaît d'elle-même dès que l'utilisateur modifie le champ
      this.formulaire.controls[champ].setErrors({ serveur: true });
      this.formulaire.controls[champ].markAsTouched();
    }
    this.erreursServeur.set(messages);

    if (champs.length === 0) {
      this.erreurGlobale.set(messageErreur(erreur));
    }
  }
}

const MESSAGES_OBLIGATOIRES: Record<ChampEquipement, string> = {
  reference: 'La référence est obligatoire.',
  nom: 'Le nom est obligatoire.',
  dateMiseService: 'La date de mise en service est obligatoire.',
  idType: 'Le type est obligatoire.',
  idZone: 'La zone est obligatoire.',
};
