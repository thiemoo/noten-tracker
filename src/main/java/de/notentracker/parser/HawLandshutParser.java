package de.notentracker.parser;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser für die "Notenblattinformation" der HAW Landshut (Prüfungsverwaltung, Stand 2026).
 *
 * <h2>Vorgehen</h2>
 * <ol>
 *   <li>Die Kopfzeile der Tabelle ({@link #HEADER_LABELS}) liefert die x-Position jeder Spalte.
 *       Alles davor – Name, Geburtsdatum usw. – wird bewusst ignoriert.</li>
 *   <li>Jedes Wort links der ersten Notenspalte gehört zum Modulnamen, jedes andere zur nächstgelegenen Spalte.</li>
 *   <li>Eine Zeile mit Modulname <em>und</em> Werten beginnt eine Tabellenzeile. Zeilenreste (z. B. "2025/26 5 5,0"
 *       unter "WiSe" oder der zweite Teil eines umgebrochenen Namens) werden der vertikal nächsten Tabellenzeile
 *       zugeordnet.</li>
 *   <li>Die Zellen werden per Regex gedeutet ({@link #GRADE}, {@link #UNGRADED_PASS}, …).</li>
 * </ol>
 * Für ein ähnliches Layout reicht es meist, die Konstanten oben anzupassen.
 */
public class HawLandshutParser implements GradeSheetParser {

    /** Präfix für Platzhalter aus "Offene Konten" (z. B. noch fehlende Studium-Generale-Punkte). */
    public static final String OPEN_ACCOUNT_PREFIX = "Offenes Konto: ";

    enum Column { PARTIAL_GRADE, FINAL_GRADE, REMARK, SEMESTER, ECTS, WEIGHT }

    private enum Section { MAIN, PRAXIS, OPEN_ACCOUNTS }

    /** Spaltenüberschriften im Notenblatt. */
    private static final Map<Column, String> HEADER_LABELS = new EnumMap<>(Map.of(
            Column.PARTIAL_GRADE, "Teilnote",
            Column.FINAL_GRADE, "Endnote",
            Column.REMARK, "Zus.",
            Column.SEMESTER, "Sem.",
            Column.ECTS, "ECTS",
            Column.WEIGHT, "Gewichtung"));

    /** "3,3" oder "4,0*" – Stern = aus anderem Studiengang angerechnet. */
    private static final Pattern GRADE = Pattern.compile("(\\d,\\d)(\\*)?");
    /** "e" = erfolgreich abgelegt (bestanden ohne Note), "e*" = angerechnet. */
    private static final Pattern UNGRADED_PASS = Pattern.compile("e(\\*)?");
    /** "_" = noch nicht abgelegt. */
    private static final String NOT_TAKEN = "_";
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:,\\d+)?");
    private static final Pattern POINTS = Pattern.compile("(\\d+(?:,\\d+)?)\\s*Points?", Pattern.CASE_INSENSITIVE);
    /** "1/2" – höchstens dreistellig, damit "2025/26" nicht als Seitenzahl gilt. */
    private static final Pattern PAGE_NUMBER = Pattern.compile("\\d{1,3}\\s*/\\s*\\d{1,3}");

    private static final Pattern OPEN_ACCOUNTS_TITLE = Pattern.compile("(?i)^Offene Konten");
    private static final Pattern PRAXIS_TITLE = Pattern.compile("(?i)^Fächer zum Praxissemester");
    private static final Pattern MAIN_TITLE = Pattern.compile("(?i)^Fächer der");
    private static final Pattern END_OF_TABLE = Pattern.compile("(?i)^Prüfungsordnung");

    /** Ab so viel Einrückung (in PDF-Punkten) gilt eine Zeile als Teilleistung eines Moduls. */
    private static final double INDENT_TOLERANCE = 6;
    /** Abstand links der ersten Notenspalte, ab dem ein Wort nicht mehr zum Namen gehört. */
    private static final double NAME_COLUMN_MARGIN = 3;

    @Override
    public String name() {
        return "HAW Landshut – Notenblattinformation";
    }

    @Override
    public boolean supports(List<TextLine> lines) {
        return lines.stream().anyMatch(line -> headerColumns(line).isPresent());
    }

    @Override
    public ParseResult parse(List<TextLine> lines) {
        List<String> warnings = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        List<Fragment> fragments = new ArrayList<>();
        Map<Column, Word> columns = null;
        Section section = Section.MAIN;

        for (TextLine line : lines) {
            Optional<Map<Column, Word>> header = headerColumns(line);
            if (header.isPresent()) {
                columns = header.get();
                continue;
            }
            if (columns == null) {
                continue; // Kopfbereich mit persönlichen Daten
            }
            String text = line.text().trim();
            if (END_OF_TABLE.matcher(text).find()) {
                break;
            }
            if (text.isEmpty() || PAGE_NUMBER.matcher(text).matches()) {
                continue;
            }

            Fragment fragment = split(line, columns);
            if (fragment.cells.isEmpty() && (line.allBold() || sectionOf(text).isPresent())) {
                section = sectionOf(text).orElse(section);
                continue;
            }
            if (!fragment.name.isEmpty() && !fragment.cells.isEmpty()) {
                rows.add(new Row(section, fragment));
            } else {
                fragments.add(fragment);
            }
        }

        if (columns == null) {
            return ParseResult.failed("Keine Notentabelle gefunden (Spalten " + String.join(", ", HEADER_LABELS.values()) + ").");
        }
        for (Fragment fragment : fragments) {
            nearestRow(rows, fragment.y).ifPresentOrElse(
                    row -> row.attach(fragment),
                    () -> warnings.add("Text ohne zugehöriges Modul ignoriert: \"" + fragment.text() + "\""));
        }

        double leftmost = rows.stream()
                .filter(row -> row.section != Section.OPEN_ACCOUNTS)
                .mapToDouble(row -> row.nameX)
                .min().orElse(0);
        List<ModuleEntry> modules = new ArrayList<>();
        for (Row row : rows) {
            boolean indented = row.nameX > leftmost + INDENT_TOLERANCE;
            toEntry(row, indented, warnings).ifPresent(modules::add);
        }
        return new ParseResult(modules, warnings);
    }

    // ---------------------------------------------------------------- Zeilen zerlegen

    private static Optional<Map<Column, Word>> headerColumns(TextLine line) {
        Map<Column, Word> columns = new EnumMap<>(Column.class);
        for (Map.Entry<Column, String> label : HEADER_LABELS.entrySet()) {
            line.words().stream()
                    .filter(word -> word.text().equals(label.getValue()))
                    .findFirst()
                    .ifPresent(word -> columns.put(label.getKey(), word));
        }
        return columns.size() == HEADER_LABELS.size() ? Optional.of(columns) : Optional.empty();
    }

    private static Fragment split(TextLine line, Map<Column, Word> columns) {
        double nameBoundary = columns.values().stream().mapToDouble(Word::x).min().orElseThrow() - NAME_COLUMN_MARGIN;
        Fragment fragment = new Fragment(line.y());
        for (Word word : line.words()) {
            if (word.x() < nameBoundary) {
                if (fragment.name.isEmpty()) {
                    fragment.nameX = word.x();
                }
                append(fragment.name, word.text());
            } else {
                Column column = nearestColumn(word, columns);
                append(fragment.cells.computeIfAbsent(column, c -> new StringBuilder()), word.text());
            }
        }
        return fragment;
    }

    private static Column nearestColumn(Word word, Map<Column, Word> columns) {
        return columns.entrySet().stream()
                .min(Comparator.comparingDouble(e -> Math.abs(e.getValue().center() - word.center())))
                .orElseThrow()
                .getKey();
    }

    private static Optional<Section> sectionOf(String text) {
        if (OPEN_ACCOUNTS_TITLE.matcher(text).find()) {
            return Optional.of(Section.OPEN_ACCOUNTS);
        }
        if (PRAXIS_TITLE.matcher(text).find()) {
            return Optional.of(Section.PRAXIS);
        }
        if (MAIN_TITLE.matcher(text).find()) {
            return Optional.of(Section.MAIN);
        }
        return Optional.empty();
    }

    private static Optional<Row> nearestRow(List<Row> rows, double y) {
        return rows.stream().min(Comparator.comparingDouble(row -> Math.abs(row.y - y)));
    }

    // ---------------------------------------------------------------- Zellen deuten

    private Optional<ModuleEntry> toEntry(Row row, boolean indented, List<String> warnings) {
        String name = row.name();
        if (row.section == Section.OPEN_ACCOUNTS) {
            return openAccount(name, row.allCells(), warnings);
        }

        String gradeText = !row.cell(Column.FINAL_GRADE).isEmpty()
                ? row.cell(Column.FINAL_GRADE)
                : row.cell(Column.PARTIAL_GRADE);
        Double grade = null;
        boolean credited = false;
        ModuleStatus status;
        Matcher graded = GRADE.matcher(gradeText);
        Matcher ungraded = UNGRADED_PASS.matcher(gradeText);
        if (graded.matches()) {
            grade = Grades.parse(graded.group(1)).orElse(null);
            credited = graded.group(2) != null;
            status = grade != null ? ModuleStatus.fromGrade(grade) : ModuleStatus.OFFEN;
        } else if (ungraded.matches()) {
            credited = ungraded.group(1) != null;
            status = ModuleStatus.BESTANDEN;
        } else {
            if (!gradeText.isEmpty() && !gradeText.equals(NOT_TAKEN)) {
                warnings.add("Note \"" + gradeText + "\" bei \"" + name + "\" nicht erkannt – als offen übernommen.");
            }
            status = ModuleStatus.OFFEN;
        }

        Double ects = number(row.cell(Column.ECTS));
        Double weight = number(row.cell(Column.WEIGHT));
        if (indented && ects == null) {
            return Optional.empty(); // Teilleistung – zählt über das übergeordnete Modul
        }

        ModuleEntry entry = new ModuleEntry(
                name,
                grade,
                ects != null ? ects : (status != ModuleStatus.BESTANDEN && weight != null ? weight : 0),
                weight,
                blankToNull(row.cell(Column.SEMESTER)),
                status);
        entry.setCredited(credited);
        entry.setRemark(blankToNull(row.cell(Column.REMARK)));
        entry.setCategory(categoryOf(row.section, indented, entry));
        return Optional.of(entry);
    }

    private static Optional<ModuleEntry> openAccount(String name, String cells, List<String> warnings) {
        String cleanName = name.replaceAll(":\\s*$", "").trim();
        Matcher points = POINTS.matcher(cells);
        if (!points.find()) {
            warnings.add("Offenes Konto \"" + cleanName + "\" ohne Punktzahl ignoriert.");
            return Optional.empty();
        }
        double value = number(points.group(1));
        String lower = cleanName.toLowerCase(Locale.GERMAN);
        ModuleCategory category = lower.contains("studium generale")
                ? ModuleCategory.STUDIUM_GENERALE
                : lower.contains("wahlpflicht") ? ModuleCategory.WAHLPFLICHT : ModuleCategory.PFLICHT;
        ModuleEntry entry = new ModuleEntry(OPEN_ACCOUNT_PREFIX + cleanName, null, value,
                category == ModuleCategory.STUDIUM_GENERALE ? null : value, null, ModuleStatus.OFFEN);
        entry.setCategory(category);
        return Optional.of(entry);
    }

    /**
     * Das Notenblatt kennt keine Kategorie "Studium Generale". Typisch dafür: eigenständige, unbenotete Module
     * ohne Gewichtung. In der Import-Vorschau lässt sich die Kategorie korrigieren.
     */
    private static ModuleCategory categoryOf(Section section, boolean indented, ModuleEntry entry) {
        if (section == Section.PRAXIS) {
            return ModuleCategory.PRAXISSEMESTER;
        }
        boolean looksLikeStudiumGenerale = !indented && entry.isPassed() && !entry.isGraded()
                && entry.getWeight() == null && entry.getEcts() > 0;
        return looksLikeStudiumGenerale ? ModuleCategory.STUDIUM_GENERALE : ModuleCategory.PFLICHT;
    }

    private static Double number(String text) {
        Matcher matcher = NUMBER.matcher(text);
        return matcher.find() ? Double.parseDouble(matcher.group().replace(',', '.')) : null;
    }

    private static String blankToNull(String text) {
        return text.isBlank() ? null : text;
    }

    private static void append(StringBuilder target, String text) {
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(text);
    }

    // ---------------------------------------------------------------- Hilfsklassen

    /** Eine zerlegte Textzeile: Namensteil und Zellinhalte. */
    private static final class Fragment {
        final double y;
        final StringBuilder name = new StringBuilder();
        double nameX;
        final Map<Column, StringBuilder> cells = new EnumMap<>(Column.class);

        Fragment(double y) {
            this.y = y;
        }

        String text() {
            StringBuilder text = new StringBuilder(name);
            cells.values().forEach(cell -> append(text, cell.toString()));
            return text.toString();
        }
    }

    /** Eine Tabellenzeile, ggf. aus mehreren PDF-Zeilen zusammengesetzt. */
    private static final class Row {
        final Section section;
        final double y;
        final double nameX;
        final List<Fragment> above = new ArrayList<>();
        final Fragment main;
        final List<Fragment> below = new ArrayList<>();

        Row(Section section, Fragment main) {
            this.section = section;
            this.y = main.y;
            this.nameX = main.nameX;
            this.main = main;
        }

        void attach(Fragment fragment) {
            (fragment.y < y ? above : below).add(fragment);
        }

        private List<Fragment> inOrder() {
            List<Fragment> all = new ArrayList<>(above);
            all.add(main);
            all.addAll(below);
            return all;
        }

        String name() {
            StringBuilder name = new StringBuilder();
            inOrder().forEach(fragment -> {
                if (!fragment.name.isEmpty()) {
                    append(name, fragment.name.toString());
                }
            });
            return name.toString();
        }

        String cell(Column column) {
            StringBuilder cell = new StringBuilder();
            inOrder().forEach(fragment -> {
                StringBuilder part = fragment.cells.get(column);
                if (part != null) {
                    append(cell, part.toString());
                }
            });
            return cell.toString();
        }

        String allCells() {
            StringBuilder text = new StringBuilder();
            for (Column column : Column.values()) {
                String cell = cell(column);
                if (!cell.isEmpty()) {
                    append(text, cell);
                }
            }
            return text.toString();
        }
    }
}
