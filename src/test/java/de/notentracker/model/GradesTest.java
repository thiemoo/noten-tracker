package de.notentracker.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GradesTest {

    @Test
    void parsesGermanAndEnglishDecimals() {
        assertEquals(Optional.of(2.3), Grades.parse("2,3"));
        assertEquals(Optional.of(1.7), Grades.parse(" 1.7 "));
        assertEquals(Optional.of(4.0), Grades.parse("4,00"));
    }

    @Test
    void rejectsInvalidInput() {
        assertEquals(Optional.empty(), Grades.parse(""));
        assertEquals(Optional.empty(), Grades.parse("bestanden"));
        assertEquals(Optional.empty(), Grades.parse("6,0"));
    }

    @Test
    void formatsWithComma() {
        assertEquals("2,3", Grades.format(2.3));
    }

    @Test
    void finalGradeIsTruncatedNotRounded() {
        assertEquals(2.3, Grades.truncateToOneDecimal(2.38));
        assertEquals(2.3, Grades.truncateToOneDecimal(2.0 + 0.3)); // 2.3000000000000003
        assertEquals(2.2, Grades.truncateToOneDecimal(2.29999));
    }

    @Test
    void duplicateKeyIgnoresWhitespaceCaseAndStatus() {
        ModuleEntry open = new ModuleEntry("Modul  Datenbanken", null, 5, 5.0, null, ModuleStatus.OFFEN);
        ModuleEntry passedLater = new ModuleEntry("modul datenbanken ", 2.3, 5, 5.0, "SoSe 2027", ModuleStatus.BESTANDEN);
        ModuleEntry other = new ModuleEntry("Datenbanken", 2.3, 5, 5.0, "SoSe 2027", ModuleStatus.BESTANDEN);

        assertEquals(open.duplicateKey(), passedLater.duplicateKey());
        assertNotEquals(open.duplicateKey(), other.duplicateKey());
    }
}
