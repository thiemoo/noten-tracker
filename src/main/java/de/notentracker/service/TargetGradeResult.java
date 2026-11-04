package de.notentracker.service;

/**
 * Ergebnis des Zielschnitt-Rechners.
 *
 * @param requiredAverage Schnitt, der in den restlichen Modulen höchstens erreicht werden darf
 *                        (NaN, wenn nichts mehr offen ist)
 * @param remainingWeight noch offene Gewichtung (≈ ECTS der noch benoteten Module)
 * @param outcome         Einordnung des Ergebnisses
 */
public record TargetGradeResult(double requiredAverage, double remainingWeight, Outcome outcome) {

    public enum Outcome {
        /** Ziel ist erreichbar, der benötigte Schnitt liegt zwischen 1,0 und 4,0. */
        ACHIEVABLE,
        /** Selbst mit 4,0 in allen restlichen Modulen wird das Ziel erreicht. */
        ALREADY_SECURE,
        /** Selbst mit 1,0 in allen restlichen Modulen ist das Ziel nicht mehr erreichbar. */
        NOT_ACHIEVABLE,
        /** Nichts mehr offen – das Ziel wurde erreicht. */
        REACHED,
        /** Nichts mehr offen – das Ziel wurde verfehlt. */
        MISSED
    }
}
