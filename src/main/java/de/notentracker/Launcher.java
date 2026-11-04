package de.notentracker;

/**
 * Einstiegspunkt der Anwendung.
 * <p>
 * Bewusst eine eigene Klasse, die NICHT von {@code Application} erbt: So startet JavaFX
 * auch vom normalen Klassenpfad aus (IntelliJ "Run", Fat-JAR, jpackage) ohne Modulpfad-Tricks.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        NotenTrackerApp.main(args);
    }
}
