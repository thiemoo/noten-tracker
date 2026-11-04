package de.notentracker.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Liest eine PDF mit PDFBox als Zeilen aus Wörtern samt Position. Die Positionen braucht der Parser,
 * um Werte den Tabellenspalten zuzuordnen – reiner Text verliert diese Information.
 */
public final class PdfLayoutReader {

    /** Abstand, um den die y-Werte jeder weiteren Seite verschoben werden, damit sie fortlaufend sind. */
    private static final double PAGE_OFFSET = 100_000;

    /**
     * @param lines            alle Textzeilen in Lesereihenfolge
     * @param pagesWithoutText Seitennummern ohne Text (z. B. gescannt oder beim Schwärzen zum Bild geworden)
     */
    public record PdfText(List<TextLine> lines, List<Integer> pagesWithoutText) {
    }

    private PdfLayoutReader() {
    }

    public static PdfText read(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            Collector collector = new Collector();
            collector.setSortByPosition(true);
            collector.getText(document);
            // Leere Seiten überspringt PDFBox ganz, daher über alle Seitennummern prüfen.
            List<Integer> pagesWithoutText = IntStream.rangeClosed(1, document.getNumberOfPages())
                    .filter(page -> !collector.pagesWithText.contains(page))
                    .boxed()
                    .toList();
            return new PdfText(collector.lines, pagesWithoutText);
        }
    }

    private static final class Collector extends PDFTextStripper {

        private final List<TextLine> lines = new ArrayList<>();
        private final Set<Integer> pagesWithText = new HashSet<>();
        private final List<Word> words = new ArrayList<>();
        private double lineY = Double.NaN;

        @Override
        protected void endPage(PDPage page) throws IOException {
            flushLine();
            super.endPage(page);
        }

        /** PDFBox liefert hier Textstücke; wir zerlegen sie an Leerzeichen in einzelne Wörter mit Position. */
        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            StringBuilder token = new StringBuilder();
            TextPosition first = null;
            TextPosition last = null;
            for (TextPosition position : positions) {
                String unicode = position.getUnicode();
                if (unicode == null || unicode.isBlank()) {
                    addWord(token, first, last);
                    token.setLength(0);
                    first = null;
                    continue;
                }
                if (first == null) {
                    first = position;
                }
                last = position;
                token.append(unicode);
            }
            addWord(token, first, last);
        }

        @Override
        protected void writeLineSeparator() {
            flushLine();
        }

        private void addWord(StringBuilder token, TextPosition first, TextPosition last) {
            if (first == null || token.isEmpty()) {
                return;
            }
            if (Double.isNaN(lineY)) {
                lineY = first.getYDirAdj() + (getCurrentPageNo() - 1) * PAGE_OFFSET;
            }
            words.add(new Word(token.toString(), first.getXDirAdj(),
                    last.getXDirAdj() + last.getWidthDirAdj(), isBold(first.getFont())));
            pagesWithText.add(getCurrentPageNo());
        }

        private void flushLine() {
            if (!words.isEmpty()) {
                lines.add(new TextLine(List.copyOf(words), lineY));
            }
            words.clear();
            lineY = Double.NaN;
        }

        private static boolean isBold(PDFont font) {
            if (font == null) {
                return false;
            }
            if (font.getName() != null && font.getName().toLowerCase(Locale.ROOT).contains("bold")) {
                return true;
            }
            PDFontDescriptor descriptor = font.getFontDescriptor();
            return descriptor != null && (descriptor.isForceBold() || descriptor.getFontWeight() >= 600);
        }
    }
}
