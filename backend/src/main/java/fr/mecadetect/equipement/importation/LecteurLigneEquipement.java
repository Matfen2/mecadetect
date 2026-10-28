package fr.mecadetect.equipement.importation;

import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

/// Découpe une ligne du fichier CSV en ses 5 colonnes, sans les valider.
///
/// Séparateur point-virgule, celui d'Excel en français ; un champ entre guillemets peut en
/// contenir. Une ligne qui n'a pas exactement 5 colonnes lève une exception : le lecteur
/// Spring Batch la transforme en `FlatFileParseException`, que le step ignore (skip).
public class LecteurLigneEquipement implements LineMapper<LigneEquipementCsv> {

    private static final String[] COLONNES = {"reference", "nom", "dateMiseService", "type", "zone"};

    private final DelimitedLineTokenizer decoupage = new DelimitedLineTokenizer(";");

    /// Crée le lecteur, en mode strict : le nombre de colonnes est vérifié.
    public LecteurLigneEquipement() {
        decoupage.setNames(COLONNES);
    }

    /// @param ligne       texte de la ligne
    /// @param numeroLigne numéro de la ligne dans le fichier, en-tête compris
    /// @return les colonnes de la ligne, sans espaces autour
    @Override
    public LigneEquipementCsv mapLine(String ligne, int numeroLigne) {
        FieldSet colonnes = decoupage.tokenize(ligne);
        return new LigneEquipementCsv(numeroLigne,
                colonnes.readString("reference"),
                colonnes.readString("nom"),
                colonnes.readString("dateMiseService"),
                colonnes.readString("type"),
                colonnes.readString("zone"));
    }
}
