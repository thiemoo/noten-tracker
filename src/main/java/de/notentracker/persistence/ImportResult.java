package de.notentracker.persistence;

/** Was ein Import in der Datenbank bewirkt hat. {@code removed} = entfernte, veraltete Platzhalter. */
public record ImportResult(int inserted, int updated, int unchanged, int removed) {

    public ImportResult withRemoved(int removed) {
        return new ImportResult(inserted, updated, unchanged, removed);
    }
}
