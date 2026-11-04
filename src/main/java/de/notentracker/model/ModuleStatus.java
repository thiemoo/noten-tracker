package de.notentracker.model;

/** Prüfungsstatus eines Moduls. */
public enum ModuleStatus {
    BESTANDEN("bestanden"),
    NICHT_BESTANDEN("nicht bestanden"),
    OFFEN("offen");

    private final String label;

    ModuleStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Leitet den Status aus einer Note ab (4,0 und besser = bestanden). */
    public static ModuleStatus fromGrade(double grade) {
        return grade <= Grades.PASS_THRESHOLD ? BESTANDEN : NICHT_BESTANDEN;
    }

    @Override
    public String toString() {
        return label;
    }
}
