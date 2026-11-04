package de.notentracker.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semesterangaben wie im Notenblatt: "WiSe 2025/26" bzw. "SoSe 2026".
 * Erkennt auch Kurzformen wie "WS 25/26" oder "SS 26", damit die zeitliche Sortierung stimmt.
 */
public final class Semester {

    // Vierstellig zuerst probieren, sonst würde aus "2025" nur "20" gelesen.
    private static final Pattern WINTER = Pattern.compile("(?i)\\b(?:WiSe|WS)\\s*(\\d{4}|\\d{2})(?:\\s*/\\s*\\d{2,4})?");
    private static final Pattern SUMMER = Pattern.compile("(?i)\\b(?:SoSe|SS)\\s*(\\d{4}|\\d{2})");

    /** Zeitliche Reihenfolge; Unbekanntes danach alphabetisch, leere Angaben ganz am Ende. */
    public static final Comparator<String> ORDER = (a, b) -> {
        OptionalInt keyA = sortKey(a);
        OptionalInt keyB = sortKey(b);
        if (keyA.isPresent() && keyB.isPresent()) {
            return Integer.compare(keyA.getAsInt(), keyB.getAsInt());
        }
        if (keyA.isPresent() != keyB.isPresent()) {
            return keyA.isPresent() ? -1 : 1;
        }
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER).compare(blankToNull(a), blankToNull(b));
    };

    private Semester() {
    }

    /** Sortierschlüssel: SoSe 2026 → 4052, WiSe 2026/27 → 4053 (Wintersemester beginnt nach dem Sommersemester). */
    public static OptionalInt sortKey(String semester) {
        if (semester == null) {
            return OptionalInt.empty();
        }
        Matcher winter = WINTER.matcher(semester);
        if (winter.find()) {
            return OptionalInt.of(fullYear(winter.group(1)) * 2 + 1);
        }
        Matcher summer = SUMMER.matcher(semester);
        if (summer.find()) {
            return OptionalInt.of(fullYear(summer.group(1)) * 2);
        }
        return OptionalInt.empty();
    }

    public static String summer(int year) {
        return "SoSe " + year;
    }

    public static String winter(int startYear) {
        return String.format("WiSe %d/%02d", startYear, (startYear + 1) % 100);
    }

    /** Vorschläge für Eingabefelder: alle Semester von {@code fromYear} bis {@code toYear}, zeitlich sortiert. */
    public static List<String> range(int fromYear, int toYear) {
        List<String> result = new ArrayList<>();
        for (int year = fromYear; year <= toYear; year++) {
            result.add(summer(year));
            result.add(winter(year));
        }
        return result;
    }

    private static int fullYear(String digits) {
        int year = Integer.parseInt(digits);
        return year < 100 ? 2000 + year : year;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
