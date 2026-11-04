package de.notentracker.service;

import de.notentracker.model.ModuleEntry;

/** Ein erkanntes Modul in der Import-Vorschau, verglichen mit dem Stand in der Datenbank. */
public record ImportCandidate(ModuleEntry entry, State state) {

    public enum State {
        NEW("neu"),
        CHANGED("geändert"),
        UNCHANGED("unverändert");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
