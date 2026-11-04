package de.notentracker.service;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import de.notentracker.model.Semester;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Bereitet die Module für die Diagramme auf – ohne JavaFX, damit es testbar bleibt. */
public class ChartData {

    private final GradeCalculator calculator;

    public ChartData(GradeCalculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Notenverlauf und ECTS-Fortschritt je Semester, zeitlich sortiert. Berücksichtigt bestandene Module
     * mit Semesterangabe – genau wie der Gesamtschnitt.
     */
    public List<SemesterPoint> semesterTrend(List<ModuleEntry> modules) {
        List<ModuleEntry> passed = modules.stream()
                .filter(ModuleEntry::isPassed)
                .filter(m -> m.getSemester() != null && !m.getSemester().isBlank())
                .toList();
        List<String> semesters = passed.stream()
                .map(ModuleEntry::getSemester)
                .distinct()
                .sorted(Semester.ORDER)
                .toList();

        List<SemesterPoint> points = new ArrayList<>();
        List<ModuleEntry> soFar = new ArrayList<>();
        for (String semester : semesters) {
            List<ModuleEntry> inSemester = passed.stream().filter(m -> semester.equals(m.getSemester())).toList();
            soFar.addAll(inSemester);
            GradeStatistics own = calculator.statistics(inSemester);
            GradeStatistics cumulative = calculator.statistics(soFar);
            points.add(new SemesterPoint(semester, own.weightedAverage(), cumulative.weightedAverage(),
                    own.earnedEcts(), cumulative.earnedEcts()));
        }
        return points;
    }

    /**
     * Wie oft kam welche Note vor? Alle üblichen Noten 1,0 … 5,0 sind enthalten (auch mit 0), dazu
     * ggf. ungewöhnliche Werte. Zählt benotete Prüfungen – auch nicht bestandene.
     *
     * @return Note → Namen der Module mit dieser Note, aufsteigend nach Note
     */
    public Map<Double, List<String>> gradeDistribution(List<ModuleEntry> modules) {
        Map<Double, List<String>> distribution = new TreeMap<>();
        Grades.VALID_GRADES.forEach(grade -> distribution.put(grade, new ArrayList<>()));
        modules.stream()
                .filter(m -> m.isGraded() && m.getStatus() != ModuleStatus.OFFEN)
                .forEach(m -> distribution.computeIfAbsent(m.getGrade(), g -> new ArrayList<>()).add(m.getName()));
        return distribution;
    }
}
