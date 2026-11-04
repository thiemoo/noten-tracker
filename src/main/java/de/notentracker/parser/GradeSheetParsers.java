package de.notentracker.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Liest eine Notenspiegel-PDF und wählt den passenden Parser. */
public final class GradeSheetParsers {

    /** Alle bekannten Layouts – neue Parser hier ergänzen. */
    private static final List<GradeSheetParser> PARSERS = List.of(new HawLandshutParser());

    private GradeSheetParsers() {
    }

    public static ParseResult parse(Path pdf) throws IOException {
        PdfLayoutReader.PdfText text = PdfLayoutReader.read(pdf);
        if (text.lines().isEmpty()) {
            return ParseResult.failed("Die PDF enthält keinen auslesbaren Text. Ist sie gescannt oder als Bild "
                    + "gespeichert (z. B. nach dem Schwärzen)? Bitte die Original-PDF aus dem Portal verwenden.");
        }

        ParseResult result = PARSERS.stream()
                .filter(parser -> parser.supports(text.lines()))
                .findFirst()
                .map(parser -> parser.parse(text.lines()))
                .orElse(ParseResult.failed("Das Layout dieses Notenspiegels ist unbekannt. Unterstützt: "
                        + PARSERS.stream().map(GradeSheetParser::name).collect(Collectors.joining(", ")) + "."));

        if (text.pagesWithoutText().isEmpty()) {
            return result;
        }
        List<String> warnings = new ArrayList<>(result.warnings());
        warnings.add(0, "Seite " + text.pagesWithoutText().stream().map(String::valueOf).collect(Collectors.joining(", "))
                + " enthält keinen Text (nur ein Bild) und wurde übersprungen – dort stehende Module fehlen.");
        return new ParseResult(result.modules(), warnings);
    }
}
