package de.notentracker.parser;

import java.util.List;

/**
 * Parser für ein bestimmtes Notenspiegel-Layout. Für ein neues Layout (andere Hochschule, neues Portal)
 * eine weitere Implementierung schreiben und in {@link GradeSheetParsers} eintragen.
 */
public interface GradeSheetParser {

    /** Anzeigename, z. B. für Fehlermeldungen. */
    String name();

    /** Erkennt dieser Parser das Layout? Wird der Reihe nach für alle Parser gefragt. */
    boolean supports(List<TextLine> lines);

    ParseResult parse(List<TextLine> lines);
}
