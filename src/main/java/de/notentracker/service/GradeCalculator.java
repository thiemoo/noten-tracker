package de.notentracker.service;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleEntry;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Berechnet Notendurchschnitt, ECTS-Summen und den Zielschnitt.
 * <p>
 * Gewichtet wird wie im HAW-Notenblatt mit der Spalte "Gewichtung" (meist = ECTS). Module ohne Gewichtung
 * – z. B. Studium Generale oder Praktika mit "e" – zählen zu den ECTS, aber nicht zum Schnitt.
 */
public class GradeCalculator {

    /** Toleranz für Gleitkomma-Vergleiche bei Noten. */
    private static final double EPSILON = 1e-9;

    public GradeStatistics statistics(List<ModuleEntry> modules) {
        double earnedEcts = 0;
        double gradedWeight = 0;
        double weightedSum = 0;
        int passed = 0;

        for (ModuleEntry module : modules) {
            if (!module.isPassed()) {
                continue;
            }
            passed++;
            earnedEcts += module.getEcts();
            if (module.countsTowardsAverage()) {
                gradedWeight += module.getWeight();
                weightedSum += module.getGrade() * module.getWeight();
            }
        }

        OptionalDouble average = gradedWeight > 0
                ? OptionalDouble.of(weightedSum / gradedWeight)
                : OptionalDouble.empty();
        return new GradeStatistics(average, earnedEcts, gradedWeight, passed);
    }

    /** Summe der Gewichtungen aller noch nicht bestandenen Module – das, was noch benotet wird. */
    public double openWeight(List<ModuleEntry> modules) {
        return modules.stream()
                .filter(m -> !m.isPassed() && m.getWeight() != null)
                .mapToDouble(ModuleEntry::getWeight)
                .sum();
    }

    /**
     * Welchen Schnitt brauche ich in den restlichen Modulen, um {@code targetAverage} zu erreichen?
     *
     * @param remainingWeight noch offene Gewichtung, z. B. {@link #openWeight(List)} oder grob 210 − erreichte ECTS
     */
    public TargetGradeResult requiredAverage(List<ModuleEntry> modules, double targetAverage, double remainingWeight) {
        GradeStatistics stats = statistics(modules);
        double weightedSum = stats.weightedAverage().orElse(0) * stats.gradedWeight();

        if (remainingWeight <= 0) {
            boolean reached = stats.weightedAverage().isPresent()
                    && stats.weightedAverage().getAsDouble() <= targetAverage + EPSILON;
            return new TargetGradeResult(Double.NaN, 0,
                    reached ? TargetGradeResult.Outcome.REACHED : TargetGradeResult.Outcome.MISSED);
        }

        double required = (targetAverage * (stats.gradedWeight() + remainingWeight) - weightedSum) / remainingWeight;

        TargetGradeResult.Outcome outcome;
        if (required < Grades.BEST - EPSILON) {
            outcome = TargetGradeResult.Outcome.NOT_ACHIEVABLE;
        } else if (required >= Grades.PASS_THRESHOLD - EPSILON) {
            outcome = TargetGradeResult.Outcome.ALREADY_SECURE;
        } else {
            outcome = TargetGradeResult.Outcome.ACHIEVABLE;
        }
        return new TargetGradeResult(required, remainingWeight, outcome);
    }
}
