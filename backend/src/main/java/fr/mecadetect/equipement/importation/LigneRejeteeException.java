package fr.mecadetect.equipement.importation;

/// Ligne du fichier d'import refusée par la validation.
///
/// Le step Spring Batch la déclare « à ignorer » (skip) : la ligne est écartée, son motif
/// est consigné dans le rapport, et l'import continue avec les lignes suivantes.
public class LigneRejeteeException extends RuntimeException {

    private final int numeroLigne;

    /// @param numeroLigne numéro de la ligne rejetée, en-tête compris
    /// @param motif       raison du rejet, affichée telle quelle à l'utilisateur
    public LigneRejeteeException(int numeroLigne, String motif) {
        super(motif);
        this.numeroLigne = numeroLigne;
    }

    /// @return le numéro de la ligne rejetée, en-tête compris
    public int getNumeroLigne() {
        return numeroLigne;
    }
}
