import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, map, of } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { DeclarationPanneRequest, Equipement, EquipementsService, InterventionsService, Priorite } from '../../core/api';
import { OPTIONS_PRIORITE } from '../../shared/libelles-intervention';
import { lireProbleme, messageErreur } from '../../shared/probleme-api';

type ChampDeclaration = 'idEquipement' | 'titre' | 'description' | 'priorite';

/** Codes métier de l'API rattachés à un champ précis du formulaire. */
const CHAMP_PAR_CODE: Record<string, ChampDeclaration> = {
  EQUIPEMENT_INTROUVABLE: 'idEquipement',
  EQUIPEMENT_ARCHIVE: 'idEquipement',
};

/**
 * Déclaration d'une panne (DEMANDEUR, ADMIN) : crée une intervention DEMANDEE
 * et passe l'équipement EN_PANNE (RG-06).
 * Depuis la fiche d'un équipement, le lien /interventions/declarer?equipement=5 le présélectionne.
 */
@Component({
  selector: 'app-declaration-panne',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './declaration-panne.html',
  styleUrl: './declaration-panne.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeclarationPanne {
  private readonly interventionsApi = inject(InterventionsService);
  private readonly equipementsApi = inject(EquipementsService);
  private readonly router = inject(Router);

  /** Paramètre de requête ?equipement=… , transmis grâce à withComponentInputBinding. */
  readonly equipement = input<string>();

  protected readonly priorites = OPTIONS_PRIORITE;

  /**
   * Équipements non archivés, par référence. 100 est la taille de page maximale du contrat :
   * suffisant pour un atelier ; au-delà, un champ de recherche avec autocomplétion prendrait le relais.
   */
  protected readonly equipements = toSignal(
    this.equipementsApi.rechercherEquipements({ page: 0, size: 100, sort: 'reference,asc' }).pipe(
      map((page) => page.contenu),
      catchError(() => of<Equipement[]>([])),
    ),
    { initialValue: [] as Equipement[] },
  );

  readonly formulaire = new FormGroup({
    idEquipement: new FormControl<number | null>(null, { validators: [Validators.required] }),
    titre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(100)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10), Validators.maxLength(1000)],
    }),
    priorite: new FormControl<string>('NORMALE', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly envoiEnCours = signal(false);
  protected readonly erreurGlobale = signal<string | null>(null);
  protected readonly erreursServeur = signal<Partial<Record<ChampDeclaration, string>>>({});

  constructor() {
    effect(() => {
      const id = Number(this.equipement());
      if (Number.isInteger(id) && id > 0) {
        this.formulaire.controls.idEquipement.setValue(id);
      }
    });
  }

  declarer(): void {
    this.erreurGlobale.set(null);
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      this.erreursServeur.set({});
      return;
    }

    const saisie = this.formulaire.getRawValue();
    const requete: DeclarationPanneRequest = {
      idEquipement: saisie.idEquipement!,
      titre: saisie.titre.trim(),
      description: saisie.description.trim(),
      priorite: saisie.priorite as Priorite,
    };

    this.envoiEnCours.set(true);
    this.interventionsApi
      .declarerPanne({ declarationPanneRequest: requete })
      .pipe(finalize(() => this.envoiEnCours.set(false)))
      .subscribe({
        next: (intervention) => void this.router.navigate(['/interventions', intervention.idIntervention]),
        error: (erreur: unknown) => this.afficherErreur(erreur),
      });
  }

  protected erreur(champ: ChampDeclaration): string | null {
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
    if (controle.hasError('minlength')) {
      return `${controle.getError('minlength').requiredLength} caractères minimum.`;
    }
    if (controle.hasError('maxlength')) {
      return `${controle.getError('maxlength').requiredLength} caractères maximum.`;
    }
    return null;
  }

  /** Erreurs de l'API sous le champ concerné quand c'est possible, en haut du formulaire sinon. */
  private afficherErreur(erreur: unknown): void {
    const probleme = lireProbleme(erreur);
    const messages: Partial<Record<ChampDeclaration, string>> = {};

    for (const erreurChamp of probleme?.erreurs ?? []) {
      if (erreurChamp.champ in this.formulaire.controls) {
        messages[erreurChamp.champ as ChampDeclaration] = erreurChamp.message;
      }
    }
    const champDuCode = probleme?.code ? CHAMP_PAR_CODE[probleme.code] : undefined;
    if (champDuCode && probleme?.detail) {
      messages[champDuCode] = probleme.detail;
    }

    const champs = Object.keys(messages) as ChampDeclaration[];
    for (const champ of champs) {
      this.formulaire.controls[champ].setErrors({ serveur: true });
      this.formulaire.controls[champ].markAsTouched();
    }
    this.erreursServeur.set(messages);

    if (champs.length === 0) {
      this.erreurGlobale.set(messageErreur(erreur));
    }
  }
}

const MESSAGES_OBLIGATOIRES: Record<ChampDeclaration, string> = {
  idEquipement: "Choisissez l'équipement en panne.",
  titre: 'Le titre est obligatoire.',
  description: 'Décrivez la panne.',
  priorite: 'La priorité est obligatoire.',
};
