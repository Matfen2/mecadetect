package fr.mecadetect.demo;

import java.util.List;

/// Fournit les comptes de démonstration à créer au démarrage.
///
/// L'interface découple l'initialiseur de l'origine des comptes : la liste en mémoire
/// peut être remplacée (fichier, autre jeu de données, double de test) sans toucher à l'initialiseur.
public interface SourceComptesDemo {

    /// Comptes de démonstration à créer.
    ///
    /// @return les comptes, sans doublon d'email
    List<CompteDemo> comptes();
}