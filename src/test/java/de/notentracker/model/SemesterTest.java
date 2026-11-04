package de.notentracker.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemesterTest {

    @Test
    void sortsChronologicallyWithUnknownAndEmptyLast() {
        List<String> semesters = new ArrayList<>(Arrays.asList(
                null, "WiSe 2026/27", "Sonderprüfung", "SoSe 2026", "WiSe 2025/26", "SS 25", ""));

        semesters.sort(Semester.ORDER);

        assertEquals(List.of("SS 25", "WiSe 2025/26", "SoSe 2026", "WiSe 2026/27", "Sonderprüfung"),
                semesters.subList(0, 5));
        // null und "" sind gleichrangig am Ende, ihre Reihenfolge untereinander ist egal.
        assertTrue(semesters.subList(5, 7).stream().allMatch(s -> s == null || s.isBlank()));
    }

    @Test
    void shortAndLongFormsAreEqual() {
        assertEquals(Semester.sortKey("WiSe 2025/26"), Semester.sortKey("WS 25/26"));
        assertEquals(Semester.sortKey("SoSe 2026"), Semester.sortKey("SS 26"));
        assertFalse(Semester.sortKey("Praxissemester").isPresent());
    }

    @Test
    void formatsLikeTheGradeSheet() {
        assertEquals("WiSe 2025/26", Semester.winter(2025));
        assertEquals("WiSe 2099/00", Semester.winter(2099));
        assertEquals(List.of("SoSe 2026", "WiSe 2026/27"), Semester.range(2026, 2026));
    }
}
