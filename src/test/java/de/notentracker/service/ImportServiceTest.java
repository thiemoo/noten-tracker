package de.notentracker.service;

import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import de.notentracker.parser.HawLandshutParser;
import de.notentracker.persistence.Database;
import de.notentracker.persistence.ImportResult;
import de.notentracker.persistence.ModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportServiceTest {

    @TempDir
    Path tempDir;

    private ModuleRepository repository;
    private ImportService service;

    @BeforeEach
    void setUp() {
        repository = new ModuleRepository(new Database(tempDir.resolve("test.db")));
        service = new ImportService(repository);
    }

    private static ModuleEntry passed(String name, double grade) {
        return new ModuleEntry(name, grade, 5, 5.0, "WiSe 2025/26", ModuleStatus.BESTANDEN);
    }

    @Test
    void comparesWithDatabase() {
        repository.insert(passed("Controlling", 4.0));
        repository.insert(passed("Analysis", 3.7));

        List<ImportCandidate> candidates = service.compare(List.of(
                passed("Controlling", 4.0), passed("Analysis", 3.3), passed("IT-Sicherheit", 2.0)));

        assertEquals(List.of(ImportCandidate.State.UNCHANGED, ImportCandidate.State.CHANGED, ImportCandidate.State.NEW),
                candidates.stream().map(ImportCandidate::state).toList());
    }

    @Test
    void keepsCategoryChosenByUser() {
        ModuleEntry ownChoice = new ModuleEntry("Rhetorik", null, 2, null, "SoSe 2026", ModuleStatus.BESTANDEN);
        ownChoice.setCategory(ModuleCategory.STUDIUM_GENERALE);
        repository.insert(ownChoice);

        ModuleEntry fromPdf = new ModuleEntry("Rhetorik", null, 2, null, "SoSe 2026", ModuleStatus.BESTANDEN);
        ImportCandidate candidate = service.compare(List.of(fromPdf)).get(0);

        assertEquals(ModuleCategory.STUDIUM_GENERALE, candidate.entry().getCategory());
        assertEquals(ImportCandidate.State.UNCHANGED, candidate.state());
    }

    @Test
    void removesOpenAccountPlaceholdersThatDisappeared() {
        String studiumGenerale = HawLandshutParser.OPEN_ACCOUNT_PREFIX + "Studium Generale";
        String wahlpflicht = HawLandshutParser.OPEN_ACCOUNT_PREFIX + "Wahlpflichtmodul Informatik";
        repository.insert(new ModuleEntry(studiumGenerale, null, 2, null, null, ModuleStatus.OFFEN));
        repository.insert(new ModuleEntry(wahlpflicht, null, 5, 5.0, null, ModuleStatus.OFFEN));
        repository.insert(new ModuleEntry("Selbst angelegt", null, 5, 5.0, null, ModuleStatus.OFFEN));

        // Neues Notenblatt: Studium Generale ist voll, das Konto taucht nicht mehr auf.
        ModuleEntry stillOpen = new ModuleEntry(wahlpflicht, null, 5, 5.0, null, ModuleStatus.OFFEN);
        ImportResult result = service.save(List.of(stillOpen), List.of(wahlpflicht));

        assertEquals(1, result.removed());
        List<String> names = repository.findAll().stream().map(ModuleEntry::getName).toList();
        assertEquals(List.of(wahlpflicht, "Selbst angelegt"), names);
        assertTrue(names.stream().noneMatch(n -> n.equals(studiumGenerale)));
    }
}
