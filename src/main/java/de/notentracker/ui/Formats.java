package de.notentracker.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

/** Deutsche Zahlenformate für die Oberfläche (Komma statt Punkt). */
final class Formats {

    private static final DecimalFormat NUMBER =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    private Formats() {
    }

    /** 5.0 → "5", 2.5 → "2,5". */
    static String number(double value) {
        return NUMBER.format(value);
    }

    /** Notenschnitt mit zwei Nachkommastellen, z. B. "3,11"; 2,725 wird zu "2,73" (nicht 2,72 wegen 2,72499…). */
    static String average(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.GERMANY, "%.2f", rounded);
    }

    /** Liest "2,5" oder "2.5"; leerer oder ungültiger Text ergibt {@code Optional.empty()}. */
    static Optional<Double> parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(text.trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
