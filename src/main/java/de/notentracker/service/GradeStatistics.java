package de.notentracker.service;

import java.util.OptionalDouble;

/**
 * Kennzahlen über alle Module.
 *
 * @param weightedAverage gewichteter Schnitt der bestandenen, benoteten Module (leer, wenn es keine gibt)
 * @param earnedEcts      ECTS aller bestandenen Module (inkl. unbenoteter)
 * @param gradedWeight    Summe der Gewichtungen, die in den Schnitt eingehen
 * @param passedModules   Anzahl bestandener Module
 */
public record GradeStatistics(OptionalDouble weightedAverage, double earnedEcts, double gradedWeight, int passedModules) {
}
