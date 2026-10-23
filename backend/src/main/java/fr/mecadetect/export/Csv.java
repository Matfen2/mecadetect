package fr.mecadetect.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/// Écriture CSV pour Excel en français, avec Apache Commons CSV : séparateur « ; » (la virgule est
/// le séparateur décimal), fin de ligne CRLF, guillemets autour des champs qui en ont besoin (RFC 4180).
final class Csv {

    static final CSVFormat FORMAT = CSVFormat.EXCEL.builder()
            .setDelimiter(';')
            .get();

    /// Marque d'ordre des octets : sans elle, Excel lit le fichier en Windows-1252 et casse les accents.
    static final String BOM = "﻿";

    /// Premiers caractères qu'Excel interprète comme le début d'une formule.
    private static final String DEBUTS_DE_FORMULE = "=+-@\t\r";

    private Csv() {
    }

    /// Une ligne CSV complète, fin de ligne comprise. Une valeur null donne un champ vide.
    static String ligne(List<String> valeurs) {
        var ligne = new StringBuilder();
        try (var imprimante = new CSVPrinter(ligne, FORMAT)) {
            imprimante.printRecord(valeurs.stream().map(Csv::neutraliserFormule).toList());
        } catch (IOException e) {
            // Impossible en écrivant dans un StringBuilder
            throw new UncheckedIOException(e);
        }
        return ligne.toString();
    }

    /// Protection contre l'injection de formule (CSV injection) : un texte saisi par un utilisateur
    /// qui commence par = + - @ est précédé d'une apostrophe, qu'Excel affiche sans l'exécuter.
    static String neutraliserFormule(String valeur) {
        if (valeur == null || valeur.isEmpty()) {
            return valeur;
        }
        return DEBUTS_DE_FORMULE.indexOf(valeur.charAt(0)) >= 0 ? "'" + valeur : valeur;
    }

    /// Flux d'octets produit ligne par ligne : la ligne suivante n'est demandée au [Stream] (et donc
    /// à la base) qu'une fois la précédente envoyée. Fermer le flux ferme le [Stream], et avec lui
    /// la requête et la connexion JDBC, y compris si le client interrompt le téléchargement.
    static InputStream enFlux(Stream<String> lignes) {
        var ouvert = new AtomicBoolean(true);
        Iterator<String> suivantes = lignes.iterator();
        Enumeration<InputStream> morceaux = new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return ouvert.get() && suivantes.hasNext();
            }

            @Override
            public InputStream nextElement() {
                return new ByteArrayInputStream(suivantes.next().getBytes(StandardCharsets.UTF_8));
            }
        };
        return new SequenceInputStream(morceaux) {
            @Override
            public void close() throws IOException {
                // SequenceInputStream.close() parcourt les morceaux restants : sans cet arrêt,
                // un téléchargement interrompu lirait quand même toutes les lignes en base
                ouvert.set(false);
                try (lignes) {
                    super.close();
                }
            }
        };
    }
}
