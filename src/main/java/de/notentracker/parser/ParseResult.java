package de.notentracker.parser;

import de.notentracker.model.ModuleEntry;

import java.util.List;

/**
 * Ergebnis eines Parser-Laufs.
 *
 * @param modules  erkannte Module (können in der Vorschau noch korrigiert werden)
 * @param warnings Hinweise für den Nutzer, z. B. nicht erkannte Noten oder Seiten ohne Text
 */
public record ParseResult(List<ModuleEntry> modules, List<String> warnings) {

    public static ParseResult failed(String reason) {
        return new ParseResult(List.of(), List.of(reason));
    }
}
