package de.notentracker.ui;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import de.notentracker.model.Semester;
import de.notentracker.service.GradeCalculator;
import de.notentracker.service.GradeStatistics;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableDoubleValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Startseite: Kennzahlen oben, darunter Filter und die Modultabelle. */
class OverviewPane extends VBox {

    private static final PseudoClass FAILED = PseudoClass.getPseudoClass("failed");
    private static final PseudoClass OPEN = PseudoClass.getPseudoClass("open");

    private final ObservableList<ModuleEntry> modules;
    private final GradeCalculator calculator;
    private final ObservableDoubleValue totalEcts;

    private final Label averageValue = new Label();
    private final Label averageDetail = new Label();
    private final Label ectsValue = new Label();
    private final ProgressBar ectsProgress = new ProgressBar(0);
    private final Label ectsDetail = new Label();
    private final Label passedValue = new Label();
    private final Label passedDetail = new Label();

    private final ComboBox<Choice<String>> semesterFilter = new ComboBox<>();
    private final ComboBox<Choice<ModuleStatus>> statusFilter = new ComboBox<>();
    private final ComboBox<Choice<ModuleCategory>> categoryFilter = new ComboBox<>();
    private final TextField search = new TextField();

    private final HBox headerActions = new HBox(8);
    private final Label selectionTitle = new Label();
    private final Label selectionStats = new Label();
    private final HBox selectionBar = new HBox(10);

    private final FilteredList<ModuleEntry> filtered;
    private final TableView<ModuleEntry> table = new TableView<>();

    OverviewPane(ObservableList<ModuleEntry> modules, GradeCalculator calculator, ObservableDoubleValue totalEcts,
                 Runnable onImport, Runnable onAdd, Consumer<ModuleEntry> onEdit, Consumer<ModuleEntry> onDelete) {
        this.modules = modules;
        this.calculator = calculator;
        this.totalEcts = totalEcts;
        this.filtered = new FilteredList<>(modules);

        getStyleClass().add("page");
        setSpacing(16);

        HBox kpis = new HBox(16,
                kpiCard("Notenschnitt", Icons.STAR, averageValue, averageDetail, null),
                kpiCard("ECTS", Icons.LAYERS, ectsValue, ectsDetail, ectsProgress),
                kpiCard("Bestandene Module", Icons.CHECK, passedValue, passedDetail, null));
        kpis.getChildren().forEach(card -> HBox.setHgrow(card, Priority.ALWAYS));

        setUpTable(onEdit, onDelete);
        VBox.setVgrow(table, Priority.ALWAYS);

        HBox toolbar = buildToolbar(onImport, onAdd, onEdit, onDelete);
        buildSelectionBar();
        VBox filterArea = new VBox(10, toolbar, selectionBar);
        getChildren().addAll(kpis, filterArea, table);

        modules.addListener((ListChangeListener<ModuleEntry>) change -> refresh());
        totalEcts.addListener((obs, old, value) -> updateKpis());
        refresh();
    }

    /** Hauptaktionen (Import, Hinzufügen) für die Kopfzeile der Seite. */
    Node headerActions() {
        return headerActions;
    }

    private static VBox kpiCard(String title, String icon, Label value, Label detail, ProgressBar progress) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-title");
        value.getStyleClass().add("kpi-value");
        detail.getStyleClass().add("kpi-detail");
        StackPane badge = new StackPane(Icons.icon(icon));
        badge.getStyleClass().add("kpi-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(new VBox(2, titleLabel, value), spacer, badge);
        head.setAlignment(Pos.TOP_LEFT);
        VBox card = new VBox(6, head);
        if (progress != null) {
            progress.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(progress);
        }
        card.getChildren().add(detail);
        card.getStyleClass().addAll("card", "kpi");
        return card;
    }

    private HBox buildToolbar(Runnable onImport, Runnable onAdd, Consumer<ModuleEntry> onEdit,
                              Consumer<ModuleEntry> onDelete) {
        semesterFilter.setPromptText("Semester");
        statusFilter.getItems().add(Choice.all("Alle Status"));
        for (ModuleStatus status : ModuleStatus.values()) {
            statusFilter.getItems().add(new Choice<>(status.label(), status));
        }
        statusFilter.getSelectionModel().selectFirst();
        categoryFilter.getItems().add(Choice.all("Alle Kategorien"));
        for (ModuleCategory category : ModuleCategory.values()) {
            categoryFilter.getItems().add(new Choice<>(category.label(), category));
        }
        categoryFilter.getSelectionModel().selectFirst();
        search.setPromptText("Modul suchen …");

        semesterFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        statusFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        categoryFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        search.textProperty().addListener((obs, old, value) -> applyFilter());

        Button importPdf = new Button("PDF importieren", Icons.icon(Icons.UPLOAD));
        importPdf.getStyleClass().add("primary");
        importPdf.setOnAction(e -> onImport.run());
        Button add = new Button("Modul hinzufügen", Icons.icon(Icons.PLUS));
        add.setOnAction(e -> onAdd.run());
        headerActions.getChildren().setAll(add, importPdf);
        headerActions.setAlignment(Pos.CENTER_RIGHT);

        Button edit = new Button("Bearbeiten");
        edit.setOnAction(e -> onEdit.accept(table.getSelectionModel().getSelectedItem()));
        Button delete = new Button("Löschen");
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> onDelete.accept(table.getSelectionModel().getSelectedItem()));
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, search, semesterFilter, statusFilter, categoryFilter, spacer, edit, delete);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        // Nichts abschneiden: lieber schrumpft das Suchfeld.
        for (Node node : List.of(semesterFilter, statusFilter, categoryFilter, edit, delete)) {
            ((Region) node).setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox.setHgrow(search, Priority.SOMETIMES);
        search.setPrefColumnCount(14);
        toolbar.getStyleClass().add("toolbar");
        return toolbar;
    }

    /** Kennzahlen nur für die gefilterte Auswahl – sichtbar, sobald ein Filter aktiv ist. */
    private void buildSelectionBar() {
        selectionTitle.getStyleClass().add("selection-title");
        selectionStats.getStyleClass().add("selection-stats");
        selectionStats.setWrapText(true);
        Button reset = new Button("Filter zurücksetzen");
        reset.getStyleClass().add("link-button");
        reset.setOnAction(e -> resetFilters());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        selectionBar.getChildren().addAll(selectionTitle, selectionStats, spacer, reset);
        selectionBar.setAlignment(Pos.CENTER_LEFT);
        selectionBar.getStyleClass().add("selection-bar");
        selectionBar.managedProperty().bind(selectionBar.visibleProperty());
        selectionBar.setVisible(false);
    }

    private void resetFilters() {
        semesterFilter.getSelectionModel().selectFirst();
        statusFilter.getSelectionModel().selectFirst();
        categoryFilter.getSelectionModel().selectFirst();
        search.clear();
    }

    private void setUpTable(Consumer<ModuleEntry> onEdit, Consumer<ModuleEntry> onDelete) {
        TableColumn<ModuleEntry, String> name = column("Modul", ModuleEntry::getName, Function.identity());
        name.setPrefWidth(300);
        name.setComparator(String.CASE_INSENSITIVE_ORDER);

        TableColumn<ModuleEntry, Double> grade = new TableColumn<>("Note");
        grade.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getGrade()));
        grade.setCellFactory(c -> new GradeCell());
        grade.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));

        TableColumn<ModuleEntry, Double> ects = column("ECTS", ModuleEntry::getEcts, Formats::number);
        TableColumn<ModuleEntry, Double> weight = column("Gewichtung", ModuleEntry::getWeight, Formats::number);
        weight.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));

        TableColumn<ModuleEntry, String> semester = column("Semester", ModuleEntry::getSemester, Function.identity());
        semester.setComparator(Semester.ORDER);
        semester.setMinWidth(112);

        TableColumn<ModuleEntry, ModuleStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStatus()));
        status.setCellFactory(c -> new StatusPillCell());
        TableColumn<ModuleEntry, ModuleCategory> category =
                column("Kategorie", ModuleEntry::getCategory, ModuleCategory::label);
        category.setMinWidth(132);
        status.setMinWidth(150);
        weight.setMinWidth(96);
        grade.setMinWidth(56);
        ects.setMinWidth(56);
        TableColumn<ModuleEntry, Boolean> credited =
                column("Angerechnet", ModuleEntry::isCredited, value -> value ? "✓" : "");
        credited.setMinWidth(104);
        TableColumn<ModuleEntry, String> remark = column("Bemerkung", ModuleEntry::getRemark, Function.identity());
        remark.setPrefWidth(160);

        for (TableColumn<ModuleEntry, ?> numeric : List.of(grade, ects, weight, credited)) {
            numeric.getStyleClass().add("center");
        }

        table.getColumns().setAll(List.of(
                name, grade, ects, weight, semester, status, category, credited, remark));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Noch keine Module – füge eins hinzu oder importiere deinen Notenspiegel."));

        SortedList<ModuleEntry> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        semester.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().add(semester);

        table.setRowFactory(t -> {
            TableRow<ModuleEntry> row = new TableRow<>() {
                @Override
                protected void updateItem(ModuleEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    pseudoClassStateChanged(FAILED, !empty && item != null && item.getStatus() == ModuleStatus.NICHT_BESTANDEN);
                    pseudoClassStateChanged(OPEN, !empty && item != null && item.getStatus() == ModuleStatus.OFFEN);
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    onEdit.accept(row.getItem());
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            ModuleEntry selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && event.getCode() == KeyCode.DELETE) {
                onDelete.accept(selected);
            } else if (selected != null && event.getCode() == KeyCode.ENTER) {
                onEdit.accept(selected);
            }
        });
    }

    /** Spalte mit Wert aus dem Modul und Formatierung für die Anzeige ({@code null} wird leer angezeigt). */
    private static <T> TableColumn<ModuleEntry, T> column(String title, Function<ModuleEntry, T> value,
                                                          Function<T, String> format) {
        TableColumn<ModuleEntry, T> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        });
        return column;
    }

    /** Status als farbige Pille – grün bestanden, rot nicht bestanden, grau offen. */
    private static final class StatusPillCell extends TableCell<ModuleEntry, ModuleStatus> {
        private final Label pill = new Label();

        StatusPillCell() {
            pill.getStyleClass().add("pill");
            pill.setMinWidth(Region.USE_PREF_SIZE);
        }

        @Override
        protected void updateItem(ModuleStatus item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            pill.setText(item.label());
            pill.getStyleClass().removeAll("pill-passed", "pill-failed", "pill-open");
            pill.getStyleClass().add(switch (item) {
                case BESTANDEN -> "pill-passed";
                case NICHT_BESTANDEN -> "pill-failed";
                case OFFEN -> "pill-open";
            });
            setGraphic(pill);
        }
    }

    /** Zeigt die Note; ohne Note "e" (bestanden, unbenotet) bzw. "–" (offen). */
    private static final class GradeCell extends TableCell<ModuleEntry, Double> {
        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            ModuleEntry module = getTableRow() == null ? null : getTableRow().getItem();
            if (empty || module == null) {
                setText(null);
            } else if (item != null) {
                setText(Grades.format(item));
            } else {
                setText(module.isPassed() ? "e" : "–");
            }
        }
    }

    private void refresh() {
        updateSemesterFilter();
        applyFilter();
        updateKpis();
    }

    /** Semester-Auswahl aus den vorhandenen Modulen neu aufbauen, Auswahl möglichst behalten. */
    private void updateSemesterFilter() {
        String selected = semesterFilter.getValue() == null ? null : semesterFilter.getValue().value();
        semesterFilter.getItems().setAll(Choice.all("Alle Semester"));
        modules.stream()
                .map(ModuleEntry::getSemester)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Semester.ORDER)
                .forEach(s -> semesterFilter.getItems().add(new Choice<>(s, s)));
        semesterFilter.getItems().stream()
                .filter(choice -> Objects.equals(choice.value(), selected))
                .findFirst()
                .ifPresentOrElse(semesterFilter::setValue, () -> semesterFilter.getSelectionModel().selectFirst());
    }

    private void applyFilter() {
        Choice<String> semester = semesterFilter.getValue();
        Choice<ModuleStatus> status = statusFilter.getValue();
        Choice<ModuleCategory> category = categoryFilter.getValue();
        String text = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.GERMAN);

        filtered.setPredicate(module ->
                (semester == null || semester.matches(module.getSemester()))
                        && (status == null || status.matches(module.getStatus()))
                        && (category == null || category.matches(module.getCategory()))
                        && (text.isEmpty() || module.getName().toLowerCase(Locale.GERMAN).contains(text)));
        updateSelectionBar(Arrays.asList(semester, status, category), text); // Filter können noch null sein
    }

    private void updateSelectionBar(List<Choice<?>> filters, String searchText) {
        List<String> active = new ArrayList<>();
        filters.stream()
                .filter(choice -> choice != null && choice.value() != null)
                .forEach(choice -> active.add(choice.label()));
        if (!searchText.isEmpty()) {
            active.add("„" + search.getText().trim() + "“");
        }
        selectionBar.setVisible(!active.isEmpty());
        if (active.isEmpty()) {
            return;
        }

        GradeStatistics stats = calculator.statistics(filtered);
        double openEcts = filtered.stream().filter(m -> !m.isPassed()).mapToDouble(ModuleEntry::getEcts).sum();
        List<String> parts = new ArrayList<>();
        parts.add(filtered.size() + (filtered.size() == 1 ? " Modul" : " Module"));
        if (stats.weightedAverage().isPresent()) {
            double average = stats.weightedAverage().getAsDouble();
            parts.add("Ø " + Formats.average(average) + " (Note " + Grades.format(Grades.truncateToOneDecimal(average)) + ")");
        } else {
            parts.add("kein Notenschnitt");
        }
        parts.add(Formats.number(stats.earnedEcts()) + " ECTS erworben");
        if (openEcts > 0) {
            parts.add(Formats.number(openEcts) + " ECTS offen");
        }
        parts.add(stats.passedModules() + " von " + filtered.size() + " bestanden");

        selectionTitle.setText(String.join(" · ", active) + ":");
        selectionStats.setText(String.join(" · ", parts));
    }

    private void updateKpis() {
        GradeStatistics stats = calculator.statistics(modules);
        double total = totalEcts.get();

        if (stats.weightedAverage().isPresent()) {
            double average = stats.weightedAverage().getAsDouble();
            averageValue.setText(Formats.average(average));
            averageDetail.setText("Gesamtnote " + Grades.format(Grades.truncateToOneDecimal(average))
                    + " · über " + Formats.number(stats.gradedWeight()) + " gewichtete ECTS");
        } else {
            averageValue.setText("–");
            averageDetail.setText("Noch keine benoteten Module");
        }

        ectsValue.setText(Formats.number(stats.earnedEcts()) + " / " + Formats.number(total));
        double progress = total > 0 ? Math.min(1, stats.earnedEcts() / total) : 0;
        ectsProgress.setProgress(progress);
        ectsDetail.setText(Math.round(progress * 100) + " % geschafft · noch "
                + Formats.number(Math.max(0, total - stats.earnedEcts())) + " ECTS");

        long open = modules.stream().filter(m -> m.getStatus() == ModuleStatus.OFFEN).count();
        long failed = modules.stream().filter(m -> m.getStatus() == ModuleStatus.NICHT_BESTANDEN).count();
        passedValue.setText(String.valueOf(stats.passedModules()));
        passedDetail.setText(open + " offen · " + failed + " nicht bestanden");
    }
}
