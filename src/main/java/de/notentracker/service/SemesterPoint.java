package de.notentracker.service;

import java.util.OptionalDouble;

/**
 * Kennzahlen eines Semesters für die Verlaufsdiagramme.
 *
 * @param semesterAverage   gewichteter Schnitt nur dieses Semesters (leer, wenn nichts benotet wurde)
 * @param cumulativeAverage Gesamtschnitt bis einschließlich dieses Semesters
 * @param earnedEcts        in diesem Semester erworbene ECTS
 * @param cumulativeEcts    bis einschließlich dieses Semesters erworbene ECTS
 */
public record SemesterPoint(String semester, OptionalDouble semesterAverage, OptionalDouble cumulativeAverage,
                            double earnedEcts, double cumulativeEcts) {
}
