package de.notentracker.service;

import de.notentracker.model.ModuleEntry;
import de.notentracker.parser.GradeSheetParsers;
import de.notentracker.parser.HawLandshutParser;
import de.notentracker.parser.ParseResult;
import de.notentracker.persistence.ImportResult;
import de.notentracker.persistence.ModuleRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ablauf des PDF-Imports: einlesen → mit der Datenbank vergleichen (Vorschau) → speichern. */
public class ImportService {

    private final ModuleRepository repository;

    public ImportService(ModuleRepository repository) {
        this.repository = repository;
    }

    public ParseResult read(Path pdf) throws IOException {
        return GradeSheetParsers.parse(pdf);
    }

    /**
     * Vergleicht erkannte Module mit der Datenbank. Bei bekannten Modulen bleibt die Kategorie erhalten,
     * die du selbst gesetzt hast – das Notenblatt kennt z. B. "Studium Generale" nicht.
     */
    public List<ImportCandidate> compare(List<ModuleEntry> parsed) {
        Map<String, ModuleEntry> existing = repository.findAll().stream()
                .collect(Collectors.toMap(ModuleEntry::duplicateKey, Function.identity()));

        List<ImportCandidate> candidates = new ArrayList<>();
        for (ModuleEntry module : parsed) {
            ModuleEntry candidate = module.copy();
            ModuleEntry known = existing.get(candidate.duplicateKey());
            if (known == null) {
                candidates.add(new ImportCandidate(candidate, ImportCandidate.State.NEW));
                continue;
            }
            candidate.setId(known.getId());
            candidate.setCategory(known.getCategory());
            ImportCandidate.State state = known.hasSameContent(candidate)
                    ? ImportCandidate.State.UNCHANGED
                    : ImportCandidate.State.CHANGED;
            candidates.add(new ImportCandidate(candidate, state));
        }
        return candidates;
    }

    /**
     * Speichert die ausgewählten Module und entfernt Platzhalter aus "Offene Konten", die im neuen Notenblatt
     * nicht mehr vorkommen (z. B. weil das Studium Generale inzwischen voll ist).
     *
     * @param selected       in der Vorschau ausgewählte (und ggf. korrigierte) Module
     * @param allParsedNames Namen aller im Notenblatt erkannten Module
     */
    public ImportResult save(List<ModuleEntry> selected, List<String> allParsedNames) {
        ImportResult result = repository.upsertAll(selected);

        Set<String> parsedKeys = allParsedNames.stream()
                .map(ModuleEntry::keyOf)
                .collect(Collectors.toSet());
        int removed = 0;
        for (ModuleEntry module : repository.findAll()) {
            if (module.getName().startsWith(HawLandshutParser.OPEN_ACCOUNT_PREFIX)
                    && !parsedKeys.contains(module.duplicateKey())) {
                repository.delete(module.getId());
                removed++;
            }
        }
        return result.withRemoved(removed);
    }
}
