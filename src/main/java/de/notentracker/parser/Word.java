package de.notentracker.parser;

/**
 * Ein Wort aus der PDF mit seiner horizontalen Lage auf der Seite.
 *
 * @param x    linker Rand
 * @param endX rechter Rand
 * @param bold in fetter Schrift (Überschriften, Abschnittstitel)
 */
public record Word(String text, double x, double endX, boolean bold) {

    public double center() {
        return (x + endX) / 2;
    }
}
