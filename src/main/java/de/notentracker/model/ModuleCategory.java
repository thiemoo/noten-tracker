package de.notentracker.model;

/** Art des Moduls im Studienplan. */
public enum ModuleCategory {
    PFLICHT("Pflichtmodul"),
    WAHLPFLICHT("Wahlpflichtmodul"),
    /** Frei wählbare Module (an der HAW Landshut 3 × 2 ECTS), in der Regel unbenotet. */
    STUDIUM_GENERALE("Studium Generale"),
    PRAXISSEMESTER("Praxissemester");

    private final String label;

    ModuleCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
