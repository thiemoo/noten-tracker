package de.notentracker.parser;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Eine Textzeile der PDF.
 *
 * @param y vertikale Lage (von oben, über alle Seiten fortlaufend), um Zeilenreste der passenden Tabellenzeile zuzuordnen
 */
public record TextLine(List<Word> words, double y) {

    public String text() {
        return words.stream().map(Word::text).collect(Collectors.joining(" "));
    }

    public boolean allBold() {
        return !words.isEmpty() && words.stream().allMatch(Word::bold);
    }
}
