package de.notentracker.service;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeCalculatorTest {

    private final GradeCalculator calculator = new GradeCalculator();

    private static ModuleEntry passed(String name, double grade, double ects) {
        return new ModuleEntry(name, grade, ects, ects, "WiSe 2025/26", ModuleStatus.BESTANDEN);
    }

    private static ModuleEntry passed(double grade, double ects) {
        return passed("Modul", grade, ects);
    }

    private static ModuleEntry ungraded(String name, double ects) {
        return new ModuleEntry(name, null, ects, null, "WiSe 2025/26", ModuleStatus.BESTANDEN);
    }

    private static ModuleEntry open(String name, double weight) {
        return new ModuleEntry(name, null, weight, weight, null, ModuleStatus.OFFEN);
    }

    @Test
    void averageIsWeighted() {
        GradeStatistics stats = calculator.statistics(List.of(passed(1.0, 5), passed(3.0, 10)));

        assertEquals(35.0 / 15.0, stats.weightedAverage().getAsDouble(), 1e-9);
        assertEquals(15, stats.earnedEcts());
        assertEquals(2, stats.passedModules());
    }

    @Test
    void failedAndOpenModulesAreIgnored() {
        ModuleEntry failed = new ModuleEntry("Lineare Algebra", 5.0, 0, 7.0, "SoSe 2026", ModuleStatus.NICHT_BESTANDEN);

        GradeStatistics stats = calculator.statistics(List.of(passed(2.0, 5), failed, open("Datenbanken", 5)));

        assertEquals(2.0, stats.weightedAverage().getAsDouble(), 1e-9);
        assertEquals(5, stats.earnedEcts());
        assertEquals(1, stats.passedModules());
    }

    @Test
    void modulesWithoutWeightCountForEctsButNotForAverage() {
        GradeStatistics stats = calculator.statistics(List.of(passed(2.0, 10), ungraded("Rhetorik", 2)));

        assertEquals(2.0, stats.weightedAverage().getAsDouble(), 1e-9);
        assertEquals(12, stats.earnedEcts());
        assertEquals(10, stats.gradedWeight());
    }

    @Test
    void noModulesMeansNoAverage() {
        GradeStatistics stats = calculator.statistics(List.of());

        assertFalse(stats.weightedAverage().isPresent());
        assertEquals(0, stats.earnedEcts());
    }

    /**
     * Beispiel im Umfang eines echten Notenblatts (Beispieldaten): benotete, unbenotete und ein nicht bestandenes
     * Modul – ergibt ECTS-Gesamt 63,0 und eine Gesamtnote von 3,1 (Schnitt 3,15 wird abgeschnitten).
     */
    @Test
    void matchesGradeSheetTotals() {
        List<ModuleEntry> modules = List.of(
                passed("Theoretische Informatik", 3.0, 5),
                passed("Analysis", 3.3, 8),
                passed("Wirtschaftsenglisch", 2.7, 5),
                passed("Marketing", 3.7, 5),
                passed("Modul Software-Technik", 3.0, 7),
                passed("Datenbanksysteme", 3.3, 5),
                new ModuleEntry("Lineare Algebra", 5.0, 0, 7.0, "SoSe 2026", ModuleStatus.NICHT_BESTANDEN),
                ungraded("Praktikum Software-Technik", 2),
                passed("Rechnungswesen", 2.3, 3),
                passed("Einführung in das wissenschaftliche Arbeiten", 3.0, 4),
                ungraded("Philosophie", 2),
                ungraded("Rhetorik", 2),
                passed("Steuern, Recht", 3.3, 5),
                passed("Controlling", 3.0, 5),
                passed("Supply Chain Management", 3.7, 5));

        GradeStatistics stats = calculator.statistics(modules);

        assertEquals(63, stats.earnedEcts());
        assertEquals(57, stats.gradedWeight());
        assertEquals(3.1, Grades.truncateToOneDecimal(stats.weightedAverage().getAsDouble()));
        assertEquals(14, stats.passedModules());
    }

    @Test
    void openWeightSumsNotPassedModules() {
        ModuleEntry failed = new ModuleEntry("Lineare Algebra", 5.0, 0, 7.0, "SoSe 2026", ModuleStatus.NICHT_BESTANDEN);
        ModuleEntry studiumGenerale = new ModuleEntry("Studium Generale", null, 2, null, null, ModuleStatus.OFFEN);

        double openWeight = calculator.openWeight(
                List.of(passed(2.0, 5), failed, open("Bachelorarbeit", 12), studiumGenerale));

        assertEquals(19, openWeight);
    }

    @Test
    void requiredAverageForReachableTarget() {
        // Gewichtung 30 mit 2,0 – um insgesamt auf 2,5 zu kommen, reicht in den restlichen 30 eine 3,0.
        TargetGradeResult result = calculator.requiredAverage(List.of(passed(2.0, 30)), 2.5, 30);

        assertEquals(3.0, result.requiredAverage(), 1e-9);
        assertEquals(30, result.remainingWeight());
        assertEquals(TargetGradeResult.Outcome.ACHIEVABLE, result.outcome());
    }

    @Test
    void targetOutOfReach() {
        TargetGradeResult result = calculator.requiredAverage(List.of(passed(3.0, 50)), 1.5, 10);

        assertTrue(result.requiredAverage() < 1.0);
        assertEquals(TargetGradeResult.Outcome.NOT_ACHIEVABLE, result.outcome());
    }

    @Test
    void targetAlreadySecure() {
        TargetGradeResult result = calculator.requiredAverage(List.of(passed(1.0, 50)), 2.0, 10);

        assertEquals(TargetGradeResult.Outcome.ALREADY_SECURE, result.outcome());
    }

    @Test
    void nothingOpenReportsReachedOrMissed() {
        List<ModuleEntry> modules = List.of(passed(2.0, 30));

        assertEquals(TargetGradeResult.Outcome.REACHED, calculator.requiredAverage(modules, 2.0, 0).outcome());
        assertEquals(TargetGradeResult.Outcome.MISSED, calculator.requiredAverage(modules, 1.7, 0).outcome());
    }
}
