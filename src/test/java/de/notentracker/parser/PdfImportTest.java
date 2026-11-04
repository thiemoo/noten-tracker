package de.notentracker.parser;

import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ende-zu-Ende: erzeugt eine PDF im Layout des HAW-Notenblatts und liest sie mit PDFBox und Parser wieder ein. */
class PdfImportTest {

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float SIZE = 9;

    @TempDir
    Path tempDir;

    @Test
    void readsGeneratedGradeSheet() throws IOException {
        Path pdf = tempDir.resolve("notenblatt.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                Writer w = new Writer(content, page.getMediaBox().getHeight());
                w.left(BOLD, 30, 60, "Notenblattinformation");
                w.left(REGULAR, 30, 80, "Name Mustermann, Max");

                w.center(BOLD, 120, 120, "Pflicht- und Wahlpflichtfächer");
                w.center(BOLD, 245, 120, "Teilnote");
                w.center(BOLD, 293, 120, "Endnote");
                w.center(BOLD, 360, 120, "Zus.");
                w.center(BOLD, 432, 120, "Sem.");
                w.center(BOLD, 481, 120, "ECTS");
                w.center(BOLD, 532, 120, "Gewichtung");
                w.left(BOLD, 30, 136, "Fächer der Hauptprüfung");

                // zweizeilige Semesterangabe
                w.left(REGULAR, 30, 155, "Theoretische Informatik");
                w.center(REGULAR, 293, 155, "3,3");
                w.center(REGULAR, 432, 150, "WiSe");
                w.center(REGULAR, 432, 160, "2025/26");
                w.center(REGULAR, 481, 155, "5");
                w.center(REGULAR, 532, 155, "5,0");

                // umgebrochener Name, Werte auf Höhe der zweiten Zeile
                w.left(REGULAR, 30, 180, "Einführung in das wissenschaftliche");
                w.left(REGULAR, 30, 190, "Arbeiten");
                w.center(REGULAR, 293, 190, "1,0");
                w.center(REGULAR, 432, 190, "SoSe 2026");
                w.center(REGULAR, 481, 190, "4");
                w.center(REGULAR, 532, 190, "4,0");

                w.left(REGULAR, 30, 210, "Controlling");
                w.center(REGULAR, 293, 210, "4,0*");
                w.center(REGULAR, 432, 205, "WiSe");
                w.center(REGULAR, 432, 215, "2025/26");
                w.center(REGULAR, 481, 210, "5");
                w.center(REGULAR, 532, 210, "5,0");

                w.left(REGULAR, 30, 230, "Bachelorarbeit");
                w.center(REGULAR, 245, 230, "_");
                w.center(REGULAR, 532, 230, "12,0");
            }
            document.addPage(new PDPage(PDRectangle.A4)); // Seite ohne Text, wie nach dem Schwärzen
            document.save(pdf.toFile());
        }

        ParseResult result = GradeSheetParsers.parse(pdf);
        List<ModuleEntry> modules = result.modules();

        assertEquals(List.of("Theoretische Informatik", "Einführung in das wissenschaftliche Arbeiten",
                "Controlling", "Bachelorarbeit"), modules.stream().map(ModuleEntry::getName).toList());
        assertEquals("WiSe 2025/26", modules.get(0).getSemester());
        assertEquals(3.3, modules.get(0).getGrade());
        assertEquals(1.0, modules.get(1).getGrade());
        assertTrue(modules.get(2).isCredited());
        assertEquals("WiSe 2025/26", modules.get(2).getSemester());
        assertEquals(ModuleStatus.OFFEN, modules.get(3).getStatus());

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("Seite 2 enthält keinen Text"));
    }

    @Test
    void pdfWithoutAnyTextGivesHelpfulMessage() throws IOException {
        Path pdf = tempDir.resolve("scan.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(pdf.toFile());
        }

        ParseResult result = GradeSheetParsers.parse(pdf);

        assertTrue(result.modules().isEmpty());
        assertTrue(result.warnings().get(0).contains("keinen auslesbaren Text"));
    }

    /** Schreibt Text mit y-Koordinate von oben gemessen, wie man ein Blatt liest. */
    private record Writer(PDPageContentStream content, float pageHeight) {

        void left(PDType1Font font, float x, float yFromTop, String text) throws IOException {
            content.beginText();
            content.setFont(font, SIZE);
            content.newLineAtOffset(x, pageHeight - yFromTop);
            content.showText(text);
            content.endText();
        }

        void center(PDType1Font font, float center, float yFromTop, String text) throws IOException {
            float width = font.getStringWidth(text) / 1000 * SIZE;
            left(font, center - width / 2, yFromTop, text);
        }
    }
}
