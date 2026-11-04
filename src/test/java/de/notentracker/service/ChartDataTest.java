package de.notentracker.service;

import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChartDataTest {

    private final ChartData chartData = new ChartData(new GradeCalculator());

    private static ModuleEntry passed(String name, double grade, double ects, String semester) {
        return new ModuleEntry(name, grade, ects, ects, semester, ModuleStatus.BESTANDEN);
    }

    @Test
    void trendIsSortedBySemesterAndCumulative() {
        List<ModuleEntry> modules = List.of(
                passed("Datenbanksysteme", 3.0, 5, "SoSe 2026"),
                passed("Analysis", 1.0, 5, "WiSe 2025/26"),
                new ModuleEntry("Praktikum", null, 2, null, "SoSe 2026", ModuleStatus.BESTANDEN),
                new ModuleEntry("Lineare Algebra", 5.0, 0, 7.0, "SoSe 2026", ModuleStatus.NICHT_BESTANDEN),
                new ModuleEntry("Datenbanken", null, 5, 5.0, null, ModuleStatus.OFFEN));

        List<SemesterPoint> trend = chartData.semesterTrend(modules);

        assertEquals(List.of("WiSe 2025/26", "SoSe 2026"), trend.stream().map(SemesterPoint::semester).toList());
        assertEquals(1.0, trend.get(0).semesterAverage().getAsDouble(), 1e-9);
        assertEquals(3.0, trend.get(1).semesterAverage().getAsDouble(), 1e-9);
        assertEquals(2.0, trend.get(1).cumulativeAverage().getAsDouble(), 1e-9);
        assertEquals(7, trend.get(1).earnedEcts());
        assertEquals(12, trend.get(1).cumulativeEcts());
    }

    @Test
    void semesterWithOnlyUngradedModulesHasNoAverage() {
        List<SemesterPoint> trend = chartData.semesterTrend(List.of(
                new ModuleEntry("Studium Generale", null, 2, null, "SoSe 2026", ModuleStatus.BESTANDEN)));

        assertFalse(trend.get(0).semesterAverage().isPresent());
        assertEquals(2, trend.get(0).cumulativeEcts());
    }

    @Test
    void distributionCountsGradedExamsIncludingFailed() {
        Map<Double, List<String>> distribution = chartData.gradeDistribution(List.of(
                passed("A", 3.3, 5, "WiSe 2025/26"),
                passed("B", 3.3, 5, "WiSe 2025/26"),
                new ModuleEntry("C", 5.0, 0, 7.0, "SoSe 2026", ModuleStatus.NICHT_BESTANDEN),
                new ModuleEntry("D", null, 2, null, "SoSe 2026", ModuleStatus.BESTANDEN)));

        assertEquals(List.of("A", "B"), distribution.get(3.3));
        assertEquals(List.of("C"), distribution.get(5.0));
        assertEquals(List.of(), distribution.get(1.0));
        assertEquals(11, distribution.size(), "alle üblichen Noten als Kategorien");
    }
}
