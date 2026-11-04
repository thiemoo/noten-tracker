package de.notentracker.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Ein Modul aus dem Notenspiegel – bestanden, nicht bestanden oder noch offen.
 * <ul>
 *   <li>{@code grade} ist {@code null} bei unbenoteten ("e" = mit Erfolg) und offenen Leistungen.</li>
 *   <li>{@code weight} ist die Gewichtung für den Notenschnitt (Spalte "Gewichtung", meist = ECTS).
 *       {@code null} heißt: zählt nicht zum Schnitt, z. B. Studium Generale.</li>
 *   <li>{@code ects} sind die erworbenen bzw. bei offenen Modulen die geplanten ECTS.</li>
 *   <li>{@code credited} = aus einem anderen Studiengang angerechnet (im Notenblatt mit "*" markiert).</li>
 * </ul>
 */
public class ModuleEntry {

    private Long id;
    private String name;
    private Double grade;
    private double ects;
    private Double weight;
    private String semester;
    private ModuleStatus status;
    private ModuleCategory category;
    private boolean credited;
    private String remark;

    public ModuleEntry() {
        this.status = ModuleStatus.OFFEN;
        this.category = ModuleCategory.PFLICHT;
    }

    public ModuleEntry(String name, Double grade, double ects, Double weight, String semester, ModuleStatus status) {
        this();
        this.name = name;
        this.grade = grade;
        this.ects = ects;
        this.weight = weight;
        this.semester = semester;
        setStatus(status);
    }

    /** Unabhängige Kopie – z. B. zum Bearbeiten, ohne das Original zu verändern. */
    public ModuleEntry copy() {
        ModuleEntry copy = new ModuleEntry(name, grade, ects, weight, semester, status);
        copy.id = id;
        copy.category = category;
        copy.credited = credited;
        copy.remark = remark;
        return copy;
    }

    public boolean isPassed() {
        return status == ModuleStatus.BESTANDEN;
    }

    public boolean isGraded() {
        return grade != null;
    }

    /** Geht dieses Modul in den Notenschnitt ein? Nur bestanden, benotet und mit Gewichtung. */
    public boolean countsTowardsAverage() {
        return isPassed() && isGraded() && weight != null && weight > 0;
    }

    /**
     * Schlüssel zur Duplikaterkennung beim erneuten Import. Der Notenspiegel führt jedes Modul genau einmal
     * (mit dem aktuellen Stand), daher reicht der normalisierte Modulname: ein erneuter Import aktualisiert
     * z. B. "offen" → "bestanden", statt einen zweiten Eintrag anzulegen.
     */
    public String duplicateKey() {
        return keyOf(name);
    }

    /** Duplikat-Schlüssel zu einem Modulnamen: ohne Leerzeichen-Unterschiede, klein geschrieben. */
    public static String keyOf(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.GERMAN);
    }

    /** Gleicher fachlicher Inhalt (alles außer der ID)? Für "unverändert" beim erneuten Import. */
    public boolean hasSameContent(ModuleEntry other) {
        return Objects.equals(name, other.name)
                && Objects.equals(grade, other.grade)
                && Double.compare(ects, other.ects) == 0
                && Objects.equals(weight, other.weight)
                && Objects.equals(semester, other.semester)
                && status == other.status
                && category == other.category
                && credited == other.credited
                && Objects.equals(remark, other.remark);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getGrade() {
        return grade;
    }

    public void setGrade(Double grade) {
        this.grade = grade;
    }

    public double getEcts() {
        return ects;
    }

    public void setEcts(double ects) {
        this.ects = ects;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public ModuleStatus getStatus() {
        return status;
    }

    public void setStatus(ModuleStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public void setCategory(ModuleCategory category) {
        this.category = Objects.requireNonNull(category);
    }

    public boolean isCredited() {
        return credited;
    }

    public void setCredited(boolean credited) {
        this.credited = credited;
    }

    /** Freitext, z. B. der Inhalt der Spalte "Zus." ("naE Frist-WiSe26/27"). */
    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "ModuleEntry{" + name + ", " + grade + ", " + ects + " ECTS, Gewichtung " + weight
                + ", " + semester + ", " + status + ", " + category + (credited ? ", angerechnet" : "") + "}";
    }
}
