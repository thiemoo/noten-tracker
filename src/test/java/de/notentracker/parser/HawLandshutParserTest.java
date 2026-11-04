package de.notentracker.parser;

import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet den Parser mit nachgebauten PDF-Zeilen. Aufbau und Zeilenumbrüche entsprechen dem echten
 * HAW-Notenblatt (so wie PDFBox es liest), die Daten sind Beispieldaten.
 */
class HawLandshutParserTest {

    // Spaltenmitten (PDF-Punkte) und Einrückung wie im Notenblatt
    private static final double NAME = 30;
    private static final double INDENT = 42;
    private static final double TEILNOTE = 245;
    private static final double ENDNOTE = 293;
    private static final double ZUS = 360;
    private static final double SEM = 432;
    private static final double ECTS = 481;
    private static final double GEWICHTUNG = 532;
    private static final double CHAR_WIDTH = 5;

    private final HawLandshutParser parser = new HawLandshutParser();

    @Test
    void parsesTypicalGradeSheet() {
        List<TextLine> lines = List.of(
                line(10, left(NAME, "Name Mustermann, Max")),
                header(100),
                line(112, bold(left(NAME, "Fächer der Hauptprüfung"))),
                // "WiSe" steht oben, "2025/26" mit ECTS/Gewichtung in der Zeile darunter
                line(124, left(NAME, "Theoretische Informatik"), at(ENDNOTE, "3,3"), at(SEM, "WiSe")),
                line(134, at(SEM, "2025/26"), at(ECTS, "5"), at(GEWICHTUNG, "5,0")),
                line(150, left(NAME, "Modul Software-Technik"), at(ENDNOTE, "3,3"), at(SEM, "SoSe 2026"),
                        at(ECTS, "7"), at(GEWICHTUNG, "7,0")),
                // Teilleistungen sind eingerückt
                line(162, left(INDENT, "Software-Technik"), at(ENDNOTE, "3,3"), at(SEM, "SoSe 2026")),
                line(174, left(INDENT, "Praktikum Software-Technik"), at(ENDNOTE, "e"), at(SEM, "SoSe 2026"),
                        at(ECTS, "2")),
                line(190, left(NAME, "Lineare Algebra"), at(ENDNOTE, "5,0"), at(ZUS, "naE")),
                line(200, at(ZUS, "Frist-SoSe27"), at(SEM, "SoSe 2026"), at(GEWICHTUNG, "7,0")),
                // umgebrochener Modulname
                line(216, left(NAME, "Einführung in das wissenschaftliche")),
                line(226, left(NAME, "Arbeiten"), at(ENDNOTE, "1,0"), at(SEM, "SoSe 2026"),
                        at(ECTS, "4"), at(GEWICHTUNG, "4,0")),
                line(242, left(NAME, "Marketing"), at(ENDNOTE, "4,0*"), at(SEM, "WiSe")),
                line(252, at(SEM, "2025/26"), at(ECTS, "5"), at(GEWICHTUNG, "5,0")),
                line(268, left(NAME, "Rhetorik"), at(ENDNOTE, "e*"), at(SEM, "WiSe")),
                line(278, at(SEM, "2025/26"), at(ECTS, "2")),
                line(290, at(GEWICHTUNG, "1/2")),
                // Seite 2
                header(100_100),
                line(100_120, left(NAME, "Bachelorarbeit"), at(TEILNOTE, "_"), at(GEWICHTUNG, "12,0")),
                line(100_132, bold(left(NAME, "Offene Konten:"))),
                line(100_144, left(NAME, "Studium Generale:"), at(ZUS, "2,0 Points")),
                line(100_156, left(NAME, "Wahlpflichtmodul Informatik:"), at(ZUS, "5,0 Points")),
                line(100_168, bold(left(NAME, "Fächer zum Praxissemester"))),
                line(100_180, left(NAME, "Praxisseminar"), at(TEILNOTE, "_"), at(GEWICHTUNG, "3,0")),
                line(100_192, bold(left(NAME, "Prüfungsordnung:"))),
                line(100_200, left(NAME, "https://www.haw-landshut.de/beispiel.pdf")));

        ParseResult result = parser.parse(lines);
        Map<String, ModuleEntry> byName = result.modules().stream()
                .collect(Collectors.toMap(ModuleEntry::getName, Function.identity()));

        assertTrue(result.warnings().isEmpty(), () -> "Unerwartete Warnungen: " + result.warnings());
        assertEquals(List.of(
                "Theoretische Informatik", "Modul Software-Technik", "Praktikum Software-Technik", "Lineare Algebra",
                "Einführung in das wissenschaftliche Arbeiten", "Marketing", "Rhetorik",
                "Bachelorarbeit", "Offenes Konto: Studium Generale", "Offenes Konto: Wahlpflichtmodul Informatik",
                "Praxisseminar"), result.modules().stream().map(ModuleEntry::getName).toList());

        ModuleEntry informatik = byName.get("Theoretische Informatik");
        assertEquals(3.3, informatik.getGrade());
        assertEquals(5, informatik.getEcts());
        assertEquals(5.0, informatik.getWeight());
        assertEquals("WiSe 2025/26", informatik.getSemester());
        assertEquals(ModuleStatus.BESTANDEN, informatik.getStatus());
        assertFalse(informatik.isCredited());

        ModuleEntry praktikum = byName.get("Praktikum Software-Technik");
        assertNull(praktikum.getGrade());
        assertEquals(ModuleStatus.BESTANDEN, praktikum.getStatus());
        assertEquals(ModuleCategory.PFLICHT, praktikum.getCategory(), "Teilleistung ist kein Studium Generale");

        ModuleEntry mathe = byName.get("Lineare Algebra");
        assertEquals(ModuleStatus.NICHT_BESTANDEN, mathe.getStatus());
        assertEquals("naE Frist-SoSe27", mathe.getRemark());
        assertEquals("SoSe 2026", mathe.getSemester());
        assertEquals(7, mathe.getEcts(), "geplante ECTS aus der Gewichtung");

        assertEquals("SoSe 2026", byName.get("Einführung in das wissenschaftliche Arbeiten").getSemester());

        ModuleEntry bwl = byName.get("Marketing");
        assertEquals(4.0, bwl.getGrade());
        assertTrue(bwl.isCredited());

        ModuleEntry creating = byName.get("Rhetorik");
        assertEquals(ModuleCategory.STUDIUM_GENERALE, creating.getCategory());
        assertTrue(creating.isCredited());
        assertNull(creating.getWeight());

        ModuleEntry thesis = byName.get("Bachelorarbeit");
        assertEquals(ModuleStatus.OFFEN, thesis.getStatus());
        assertEquals(12, thesis.getEcts());
        assertEquals(12.0, thesis.getWeight());

        ModuleEntry studiumGenerale = byName.get("Offenes Konto: Studium Generale");
        assertEquals(ModuleCategory.STUDIUM_GENERALE, studiumGenerale.getCategory());
        assertEquals(2, studiumGenerale.getEcts());
        assertNull(studiumGenerale.getWeight());
        assertEquals(5.0, byName.get("Offenes Konto: Wahlpflichtmodul Informatik").getWeight());

        assertEquals(ModuleCategory.PRAXISSEMESTER, byName.get("Praxisseminar").getCategory());
        assertTrue(result.modules().stream().noneMatch(m -> m.getName().contains("Mustermann")),
                "Kopfbereich mit persönlichen Daten wird ignoriert");
    }

    @Test
    void attachesLineFragmentsAboveAndBelowToTheNearestRow() {
        List<TextLine> lines = List.of(
                header(100),
                line(118, at(SEM, "WiSe")),
                line(124, left(NAME, "Controlling"), at(ENDNOTE, "4,0*"), at(ECTS, "5"), at(GEWICHTUNG, "5,0")),
                line(130, at(SEM, "2025/26")),
                line(160, left(NAME, "Analysis"), at(ENDNOTE, "3,7"), at(SEM, "SoSe 2026"),
                        at(ECTS, "8"), at(GEWICHTUNG, "8,0")));

        List<ModuleEntry> modules = parser.parse(lines).modules();

        assertEquals("WiSe 2025/26", modules.get(0).getSemester());
        assertEquals("SoSe 2026", modules.get(1).getSemester());
    }

    @Test
    void unknownGradeIsReportedAndImportedAsOpen() {
        List<TextLine> lines = List.of(
                header(100),
                line(120, left(NAME, "Datenbanken"), at(ENDNOTE, "n.b."), at(GEWICHTUNG, "5,0")));

        ParseResult result = parser.parse(lines);

        assertEquals(ModuleStatus.OFFEN, result.modules().get(0).getStatus());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("n.b."));
    }

    @Test
    void otherLayoutsAreNotSupported() {
        List<TextLine> lines = List.of(line(10, left(NAME, "Modul Note ECTS")));

        assertFalse(parser.supports(lines));
        assertTrue(parser.parse(lines).modules().isEmpty());
    }

    // ---------------------------------------------------------------- Hilfsfunktionen

    private static TextLine header(double y) {
        List<Word> words = new ArrayList<>(at(120, "Pflicht- und Wahlpflichtfächer"));
        words.addAll(at(TEILNOTE, "Teilnote"));
        words.addAll(at(ENDNOTE, "Endnote"));
        words.addAll(at(ZUS, "Zus."));
        words.addAll(at(SEM, "Sem."));
        words.addAll(at(ECTS, "ECTS"));
        words.addAll(at(GEWICHTUNG, "Gewichtung"));
        return new TextLine(bold(words), y);
    }

    @SafeVarargs
    private static TextLine line(double y, List<Word>... parts) {
        return new TextLine(Arrays.stream(parts).flatMap(List::stream).toList(), y);
    }

    /** Text linksbündig ab {@code x}. */
    private static List<Word> left(double x, String text) {
        List<Word> words = new ArrayList<>();
        double position = x;
        for (String token : text.split(" ")) {
            double width = token.length() * CHAR_WIDTH;
            words.add(new Word(token, position, position + width, false));
            position += width + CHAR_WIDTH;
        }
        return words;
    }

    /** Text zentriert um {@code center} – so stehen Werte in den Notenspalten. */
    private static List<Word> at(double center, String text) {
        return left(center - text.length() * CHAR_WIDTH / 2, text);
    }

    private static List<Word> bold(List<Word> words) {
        return words.stream().map(w -> new Word(w.text(), w.x(), w.endX(), true)).toList();
    }
}
