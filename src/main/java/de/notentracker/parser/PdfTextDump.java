package de.notentracker.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

/**
 * Entwickler-Werkzeug: gibt den Text einer PDF so aus, wie PDFBox ihn sieht – Zeile für Zeile mit Nummer.
 * Hilfreich, um den Parser an ein neues Notenspiegel-Layout anzupassen.
 * <p>
 * Aufruf: {@code PdfTextDump <pfad-zur-pdf>}
 */
public final class PdfTextDump {

    private PdfTextDump() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Aufruf: PdfTextDump <pfad-zur-pdf>");
            System.exit(1);
        }
        try (PDDocument document = Loader.loadPDF(new File(args[0]))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String[] lines = stripper.getText(document).split("\\R");
            for (int i = 0; i < lines.length; i++) {
                System.out.printf("%3d | %s%n", i + 1, lines[i]);
            }
        }
    }
}
