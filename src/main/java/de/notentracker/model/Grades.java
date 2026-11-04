package de.notentracker.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Hilfsfunktionen rund um deutsche Noten (1,0 – 5,0). */
public final class Grades {

    public static final double BEST = 1.0;
    public static final double WORST = 5.0;
    public static final double PASS_THRESHOLD = 4.0;

    /** Zulässige Einzelnoten nach üblicher Prüfungsordnung. */
    public static final Set<Double> VALID_GRADES =
            Set.of(1.0, 1.3, 1.7, 2.0, 2.3, 2.7, 3.0, 3.3, 3.7, 4.0, 5.0);

    private Grades() {
    }

    public static boolean isValid(double grade) {
        return VALID_GRADES.contains(grade);
    }

    public static boolean isPassing(double grade) {
        return grade >= BEST && grade <= PASS_THRESHOLD;
    }

    /** Liest "2,3", "2.3" oder " 2,30 " ein. Leerer oder ungültiger Text ergibt {@code Optional.empty()}. */
    public static Optional<Double> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            double value = new BigDecimal(text.trim().replace(',', '.'))
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
            return value >= BEST && value <= WORST ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Formatiert eine Note deutsch mit Komma, z. B. 2.3 → "2,3". */
    public static String format(double grade) {
        return String.format(Locale.GERMANY, "%.1f", grade);
    }

    /**
     * Gesamtnote wie in den meisten Prüfungsordnungen: nur die erste Nachkommastelle zählt,
     * alle weiteren werden ohne Rundung gestrichen (2,38 → 2,3).
     */
    public static double truncateToOneDecimal(double average) {
        // Erst auf 6 Stellen runden, damit Gleitkomma-Artefakte (2,2999999) nicht falsch abgeschnitten werden.
        return BigDecimal.valueOf(average)
                .setScale(6, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.DOWN)
                .doubleValue();
    }
}
