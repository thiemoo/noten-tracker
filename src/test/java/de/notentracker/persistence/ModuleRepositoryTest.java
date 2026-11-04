package de.notentracker.persistence;

import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRepositoryTest {

    @TempDir
    Path tempDir;

    private Database database;
    private ModuleRepository repository;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("test.db"));
        repository = new ModuleRepository(database);
    }

    private static ModuleEntry controlling() {
        ModuleEntry module = new ModuleEntry("Controlling", 4.0, 5, 5.0, "WiSe 2025/26", ModuleStatus.BESTANDEN);
        module.setCredited(true);
        return module;
    }

    private static ModuleEntry studiumGenerale() {
        ModuleEntry module = new ModuleEntry("Rhetorik", null, 2, null, "WiSe 2025/26", ModuleStatus.BESTANDEN);
        module.setCategory(ModuleCategory.STUDIUM_GENERALE);
        module.setCredited(true);
        return module;
    }

    @Test
    void insertedModuleIsLoadedWithAllFields() {
        ModuleEntry failed = new ModuleEntry("Lineare Algebra", 5.0, 0, 7.0, "SoSe 2026", ModuleStatus.NICHT_BESTANDEN);
        failed.setRemark("naE Frist-SoSe27");

        repository.insert(controlling());
        repository.insert(studiumGenerale());
        repository.insert(failed);

        List<ModuleEntry> all = repository.findAll();
        assertEquals(3, all.size());
        assertNotNull(all.get(0).getId());
        assertTrue(all.get(0).hasSameContent(controlling()));
        assertTrue(all.get(1).hasSameContent(studiumGenerale()), "null-Werte für Note/Gewichtung bleiben null");
        assertTrue(all.get(2).hasSameContent(failed));
    }

    @Test
    void updateAndDelete() {
        ModuleEntry module = controlling();
        repository.insert(module);

        module.setGrade(3.7);
        repository.update(module);
        assertEquals(3.7, repository.findAll().get(0).getGrade());

        repository.delete(module.getId());
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void sameNameCannotBeInsertedTwice() {
        repository.insert(controlling());

        ModuleEntry duplicate = controlling();
        duplicate.setName("  controlling ");
        assertThrows(DataAccessException.class, () -> repository.insert(duplicate));
    }

    @Test
    void reimportUpdatesInsteadOfDuplicating() {
        ModuleEntry openDatabases = new ModuleEntry("Modul Datenbanken", null, 5, 5.0, null, ModuleStatus.OFFEN);
        ImportResult first = repository.upsertAll(List.of(controlling(), openDatabases));
        assertEquals(new ImportResult(2, 0, 0, 0), first);

        ModuleEntry passedDatabases = new ModuleEntry("Modul Datenbanken", 2.3, 5, 5.0, "SoSe 2027", ModuleStatus.BESTANDEN);
        ModuleEntry newModule = new ModuleEntry("IT-Sicherheit", 1.7, 5, 5.0, "SoSe 2027", ModuleStatus.BESTANDEN);
        ImportResult second = repository.upsertAll(List.of(controlling(), passedDatabases, newModule));

        assertEquals(new ImportResult(1, 1, 1, 0), second);
        List<ModuleEntry> all = repository.findAll();
        assertEquals(3, all.size());
        assertEquals(ModuleStatus.BESTANDEN, repository.findByKey("modul datenbanken").orElseThrow().getStatus());
    }

    @Test
    void dataSurvivesReopeningTheDatabase() {
        repository.insert(controlling());

        ModuleRepository reopened = new ModuleRepository(new Database(tempDir.resolve("test.db")));

        assertEquals(1, reopened.findAll().size());
    }

    @Test
    void settingsFallBackToDefaultAndCanBeOverwritten() {
        SettingsRepository settings = new SettingsRepository(database);
        assertEquals(210, settings.getDouble(SettingsRepository.TOTAL_ECTS, SettingsRepository.DEFAULT_TOTAL_ECTS));

        settings.setDouble(SettingsRepository.TARGET_AVERAGE, 2.5);
        settings.setDouble(SettingsRepository.TARGET_AVERAGE, 2.3);

        assertEquals(2.3, settings.getDouble(SettingsRepository.TARGET_AVERAGE, 0));
    }
}
