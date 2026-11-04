package de.notentracker.ui;

import de.notentracker.model.ModuleEntry;
import de.notentracker.parser.ParseResult;
import de.notentracker.persistence.DataAccessException;
import de.notentracker.persistence.ImportResult;
import de.notentracker.persistence.ModuleRepository;
import de.notentracker.persistence.SettingsRepository;
import de.notentracker.service.GradeCalculator;
import de.notentracker.service.ImportService;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hauptfenster: Seitenleiste mit Navigation, Studienfortschritt und Hell/Dunkel-Schalter, rechts die aktive Seite.
 * Hält die Modulliste und verbindet Oberfläche und Datenbank.
 */
public class MainView {

    private static final double DEFAULT_TARGET_AVERAGE = 2.5;

    /** Eine Seite der App mit Titel, Untertitel, Icon für die Navigation und optionalen Aktionen oben rechts. */
    private record Page(String title, String subtitle, String icon, Node content, Node actions) {
    }

    private final ModuleRepository repository;
    private final ImportService importService;
    private final GradeCalculator calculator;
    private final ThemeManager theme;
    private final ObservableList<ModuleEntry> modules = FXCollections.observableArrayList();
    private final DoubleProperty totalEcts;
    private final BorderPane root = new BorderPane();

    private final Label progressText = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);

    public MainView(ModuleRepository repository, SettingsRepository settings, GradeCalculator calculator,
                    ImportService importService, ThemeManager theme) {
        this.repository = repository;
        this.importService = importService;
        this.calculator = calculator;
        this.theme = theme;

        totalEcts = new SimpleDoubleProperty(
                settings.getDouble(SettingsRepository.TOTAL_ECTS, SettingsRepository.DEFAULT_TOTAL_ECTS));
        DoubleProperty targetAverage = new SimpleDoubleProperty(
                settings.getDouble(SettingsRepository.TARGET_AVERAGE, DEFAULT_TARGET_AVERAGE));
        totalEcts.addListener((obs, old, value) -> settings.setDouble(SettingsRepository.TOTAL_ECTS, value.doubleValue()));
        targetAverage.addListener((obs, old, value) ->
                settings.setDouble(SettingsRepository.TARGET_AVERAGE, value.doubleValue()));

        OverviewPane overview = new OverviewPane(modules, calculator, totalEcts,
                this::importPdf, this::addModule, this::editModule, this::deleteModule);
        List<Page> pages = List.of(
                new Page("Übersicht", "Alle Module und dein aktueller Stand", Icons.OVERVIEW,
                        overview, overview.headerActions()),
                new Page("Diagramme", "Notenverlauf, Verteilung und ECTS-Fortschritt", Icons.CHARTS,
                        new ChartsPane(modules, calculator, totalEcts), null),
                new Page("Zielschnitt", "Was brauchst du noch für deinen Wunschschnitt?", Icons.TARGET,
                        new TargetCalculatorPane(modules, calculator, totalEcts, targetAverage), null));

        root.setLeft(buildSidebar(pages));
        root.getStyleClass().add("main");

        modules.addListener((ListChangeListener<ModuleEntry>) change -> updateProgress());
        totalEcts.addListener((obs, old, value) -> updateProgress());
        reload();
    }

    public Parent getRoot() {
        return root;
    }

    // ---------------------------------------------------------------- Seitenleiste

    private VBox buildSidebar(List<Page> pages) {
        ImageView logo = new ImageView(new Image(
                Objects.requireNonNull(getClass().getResource("/de/notentracker/ui/icon.png")).toExternalForm(),
                32, 32, true, true));
        Label name = new Label("Noten-Tracker");
        name.getStyleClass().add("brand-name");
        Label tagline = new Label("Dein Studium im Blick");
        tagline.getStyleClass().add("brand-tagline");
        HBox brand = new HBox(10, logo, new VBox(0, name, tagline));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");

        VBox nav = new VBox(4);
        ToggleGroup group = new ToggleGroup();
        for (Page page : pages) {
            ToggleButton item = new ToggleButton(page.title(), Icons.icon(page.icon()));
            item.setToggleGroup(group);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("nav-item");
            item.setUserData(page);
            nav.getChildren().add(item);
        }
        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                group.selectToggle(old); // immer genau eine Seite aktiv
            } else {
                showPage((Page) selected.getUserData());
            }
        });
        group.selectToggle(group.getToggles().get(0));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(20, brand, nav, spacer, buildProgressBox(), buildThemeSwitch());
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(232);
        sidebar.setMinWidth(232);
        return sidebar;
    }

    private VBox buildProgressBox() {
        Label title = new Label("Studienfortschritt");
        title.getStyleClass().add("sidebar-label");
        progressText.getStyleClass().add("sidebar-progress-text");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(6, title, progressBar, progressText);
        box.getStyleClass().add("sidebar-box");
        return box;
    }

    private VBox buildThemeSwitch() {
        Label title = new Label("Darstellung");
        title.getStyleClass().add("sidebar-label");
        HBox segmented = new HBox();
        segmented.getStyleClass().add("segmented");
        ToggleGroup group = new ToggleGroup();
        for (ThemeManager.Mode mode : ThemeManager.Mode.values()) {
            ToggleButton button = new ToggleButton(mode.label());
            button.setToggleGroup(group);
            button.setUserData(mode);
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
            segmented.getChildren().add(button);
            if (mode == theme.modeProperty().get()) {
                group.selectToggle(button);
            }
        }
        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                group.selectToggle(old);
            } else {
                theme.modeProperty().set((ThemeManager.Mode) selected.getUserData());
            }
        });
        return new VBox(8, title, segmented);
    }

    private void showPage(Page page) {
        Label title = new Label(page.title());
        title.getStyleClass().add("page-title");
        Label subtitle = new Label(page.subtitle());
        subtitle.getStyleClass().add("page-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(new VBox(2, title, subtitle), spacer);
        if (page.actions() != null) {
            header.getChildren().add(page.actions());
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");

        VBox.setVgrow(page.content(), Priority.ALWAYS);
        VBox content = new VBox(header, page.content());
        content.getStyleClass().add("content");
        root.setCenter(content);
    }

    private void updateProgress() {
        double earned = calculator.statistics(modules).earnedEcts();
        double total = totalEcts.get();
        progressBar.setProgress(total > 0 ? Math.min(1, earned / total) : 0);
        progressText.setText(Formats.number(earned) + " / " + Formats.number(total) + " ECTS · "
                + Math.round(total > 0 ? earned / total * 100 : 0) + " %");
    }

    // ---------------------------------------------------------------- Aktionen

    private void reload() {
        modules.setAll(repository.findAll());
    }

    private void importPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Notenspiegel (PDF) auswählen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
        Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) {
            chooser.setInitialDirectory(downloads.toFile());
        }
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        ParseResult parsed;
        try {
            parsed = importService.read(file.toPath());
        } catch (IOException e) {
            showMessage(Alert.AlertType.ERROR, "PDF konnte nicht gelesen werden", e.getMessage());
            return;
        }
        if (parsed.modules().isEmpty()) {
            showMessage(Alert.AlertType.WARNING, "Keine Module erkannt", String.join("\n\n", parsed.warnings()));
            return;
        }

        ImportPreviewDialog preview = new ImportPreviewDialog(importService.compare(parsed.modules()), parsed.warnings());
        prepare(preview);
        List<String> parsedNames = parsed.modules().stream().map(ModuleEntry::getName).toList();
        preview.showAndWait().ifPresent(selected -> {
            try {
                ImportResult result = importService.save(selected, parsedNames);
                showMessage(Alert.AlertType.INFORMATION, "Import abgeschlossen",
                        result.inserted() + " neu · " + result.updated() + " aktualisiert · "
                                + result.unchanged() + " unverändert"
                                + (result.removed() > 0 ? " · " + result.removed() + " erledigte offene Konten entfernt" : ""));
            } catch (DataAccessException e) {
                showMessage(Alert.AlertType.ERROR, "Speichern fehlgeschlagen", e.getMessage());
            }
            reload();
        });
    }

    /** Besitzerfenster und Hell/Dunkel-Modus für jeden Dialog setzen. */
    private void prepare(Dialog<?> dialog) {
        dialog.initOwner(root.getScene().getWindow());
        theme.style(dialog);
    }

    private void showMessage(Alert.AlertType type, String header, String text) {
        Alert alert = new Alert(type, text);
        alert.setHeaderText(header);
        prepare(alert);
        alert.showAndWait();
    }

    private void addModule() {
        dialog(null).showAndWait().ifPresent(module -> save(() -> repository.insert(module)));
    }

    private void editModule(ModuleEntry module) {
        if (module == null) {
            return;
        }
        dialog(module).showAndWait().ifPresent(changed -> save(() -> repository.update(changed)));
    }

    private void deleteModule(ModuleEntry module) {
        if (module == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "\"" + module.getName() + "\" wirklich löschen?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Modul löschen");
        prepare(confirm);
        confirm.showAndWait()
                .filter(button -> button == ButtonType.YES)
                .ifPresent(button -> save(() -> repository.delete(module.getId())));
    }

    private ModuleDialog dialog(ModuleEntry module) {
        Set<String> semesters = modules.stream()
                .map(ModuleEntry::getSemester)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        ModuleDialog dialog = new ModuleDialog(module, semesters, this::isDuplicate);
        prepare(dialog);
        return dialog;
    }

    /** Gibt es ein anderes Modul (andere ID) mit demselben Namen? */
    private boolean isDuplicate(ModuleEntry candidate) {
        return modules.stream().anyMatch(existing ->
                existing.duplicateKey().equals(candidate.duplicateKey())
                        && !Objects.equals(existing.getId(), candidate.getId()));
    }

    private void save(Runnable action) {
        try {
            action.run();
        } catch (DataAccessException e) {
            showMessage(Alert.AlertType.ERROR, "Speichern fehlgeschlagen", e.getMessage());
        }
        reload();
    }
}
