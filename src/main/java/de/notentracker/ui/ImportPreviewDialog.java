package de.notentracker.ui;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import de.notentracker.service.ImportCandidate;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Vorschau vor dem Speichern: zeigt alle erkannten Module, markiert neu/geändert/unverändert und erlaubt,
 * falsch erkannte Werte direkt in der Tabelle zu korrigieren (Doppelklick auf eine Zelle).
 */
class ImportPreviewDialog extends Dialog<List<ModuleEntry>> {

    private final ObservableList<PreviewRow> rows;
    private final TableView<PreviewRow> table = new TableView<>();
    private final Label error = new Label();

    ImportPreviewDialog(List<ImportCandidate> candidates, List<String> warnings) {
        setTitle("Notenspiegel importieren");
        setHeaderText(null);
        setResizable(true);

        ButtonType importButton = new ButtonType("Importieren", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(importButton, ButtonType.CANCEL);
        getDialogPane().setPrefSize(1150, 680);

        rows = FXCollections.observableArrayList(candidates.stream().map(PreviewRow::new).toList());

        VBox content = new VBox(12, buildSummary(candidates));
        if (!warnings.isEmpty()) {
            content.getChildren().add(buildWarnings(warnings));
        }
        Label hint = new Label("Prüfe die erkannten Werte. Doppelklick auf eine Zelle zum Korrigieren, "
                + "Haken entfernen, um ein Modul nicht zu übernehmen.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        setUpTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        error.getStyleClass().add("form-error");
        content.getChildren().addAll(hint, buildSelectionButtons(), table, error);
        getDialogPane().setContent(content);

        getDialogPane().lookupButton(importButton).getStyleClass().add("primary");
        getDialogPane().lookupButton(importButton).addEventFilter(ActionEvent.ACTION, event -> {
            Optional<String> problem = validate();
            if (problem.isPresent()) {
                error.setText(problem.get());
                event.consume();
            }
        });
        setResultConverter(button -> button == importButton
                ? rows.stream().filter(row -> row.selected.get()).map(PreviewRow::toEntry).toList()
                : null);
    }

    private static Label buildSummary(List<ImportCandidate> candidates) {
        Map<ImportCandidate.State, Long> counts = candidates.stream()
                .collect(Collectors.groupingBy(ImportCandidate::state, Collectors.counting()));
        Label summary = new Label(candidates.size() + " Module erkannt · "
                + counts.getOrDefault(ImportCandidate.State.NEW, 0L) + " neu · "
                + counts.getOrDefault(ImportCandidate.State.CHANGED, 0L) + " geändert · "
                + counts.getOrDefault(ImportCandidate.State.UNCHANGED, 0L) + " unverändert");
        summary.getStyleClass().add("card-title");
        return summary;
    }

    private static VBox buildWarnings(List<String> warnings) {
        VBox box = new VBox(4);
        box.getStyleClass().add("warning-box");
        for (String warning : warnings) {
            Label label = new Label("⚠ " + warning);
            label.setWrapText(true);
            box.getChildren().add(label);
        }
        return box;
    }

    private HBox buildSelectionButtons() {
        Button all = new Button("Alle auswählen");
        all.setOnAction(e -> rows.forEach(row -> row.selected.set(true)));
        Button changes = new Button("Nur neue und geänderte");
        changes.setOnAction(e -> rows.forEach(row -> row.selected.set(row.state != ImportCandidate.State.UNCHANGED)));
        Button none = new Button("Keine");
        none.setOnAction(e -> rows.forEach(row -> row.selected.set(false)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(8, all, changes, none, spacer);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void setUpTable() {
        TableColumn<PreviewRow, Boolean> selected = new TableColumn<>("✓");
        selected.setCellValueFactory(c -> c.getValue().selected);
        selected.setCellFactory(CheckBoxTableCell.forTableColumn(selected));
        selected.setPrefWidth(40);

        TableColumn<PreviewRow, ImportCandidate.State> state = new TableColumn<>("Änderung");
        state.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().state));
        state.setCellFactory(c -> new StateCell());
        state.setEditable(false);

        TableColumn<PreviewRow, String> name = textColumn("Modul", row -> row.name, text -> !text.isBlank(),
                "Der Modulname darf nicht leer sein.");
        name.setPrefWidth(300);

        TableColumn<PreviewRow, String> grade = textColumn("Note", row -> row.grade,
                text -> text.isBlank() || Grades.parse(text).isPresent(), "Die Note muss zwischen 1,0 und 5,0 liegen.");
        // Eine geänderte Note passt den Status an.
        grade.setOnEditCommit(event -> {
            if (commit(event, text -> text.isBlank() || Grades.parse(text).isPresent(),
                    "Die Note muss zwischen 1,0 und 5,0 liegen.")) {
                Grades.parse(event.getNewValue())
                        .ifPresent(g -> event.getRowValue().status.set(ModuleStatus.fromGrade(g)));
            }
        });

        TableColumn<PreviewRow, String> ects = textColumn("ECTS", row -> row.ects,
                text -> Formats.parseNumber(text).filter(v -> v >= 0).isPresent(), "ECTS müssen eine Zahl sein.");
        TableColumn<PreviewRow, String> weight = textColumn("Gewichtung", row -> row.weight,
                text -> text.isBlank() || Formats.parseNumber(text).filter(v -> v >= 0).isPresent(),
                "Die Gewichtung muss eine Zahl sein – oder leer bleiben.");
        TableColumn<PreviewRow, String> semester = textColumn("Semester", row -> row.semester, text -> true, "");

        TableColumn<PreviewRow, ModuleStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> c.getValue().status);
        status.setCellFactory(ComboBoxTableCell.forTableColumn(ModuleStatus.values()));

        TableColumn<PreviewRow, ModuleCategory> category = new TableColumn<>("Kategorie");
        category.setCellValueFactory(c -> c.getValue().category);
        category.setCellFactory(ComboBoxTableCell.forTableColumn(ModuleCategory.values()));
        category.setPrefWidth(140);

        TableColumn<PreviewRow, Boolean> credited = new TableColumn<>("Angerechnet");
        credited.setCellValueFactory(c -> c.getValue().credited);
        credited.setCellFactory(CheckBoxTableCell.forTableColumn(credited));

        TableColumn<PreviewRow, String> remark = textColumn("Bemerkung", row -> row.remark, text -> true, "");

        for (TableColumn<PreviewRow, ?> centered : List.of(selected, grade, ects, weight, credited)) {
            centered.getStyleClass().add("center");
        }
        table.getColumns().setAll(List.of(
                selected, state, name, grade, ects, weight, semester, status, category, credited, remark));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setEditable(true);
        table.setItems(rows);
    }

    /** Editierbare Textspalte, die ungültige Eingaben verwirft und einen Hinweis zeigt. */
    private TableColumn<PreviewRow, String> textColumn(String title, Function<PreviewRow, StringProperty> property,
                                                       Predicate<String> valid, String message) {
        TableColumn<PreviewRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> property.apply(c.getValue()));
        column.setCellFactory(TextFieldTableCell.forTableColumn());
        column.setOnEditCommit(event -> commit(event, valid, message));
        return column;
    }

    private boolean commit(TableColumn.CellEditEvent<PreviewRow, String> event, Predicate<String> valid,
                           String message) {
        String text = event.getNewValue() == null ? "" : event.getNewValue().trim();
        if (!valid.test(text)) {
            error.setText(message);
            table.refresh();
            return false;
        }
        error.setText("");
        StringProperty target = (StringProperty) event.getTableColumn().getCellObservableValue(event.getRowValue());
        target.set(text);
        event.getRowValue().selected.set(true); // Wer korrigiert, will die Zeile auch übernehmen.
        return true;
    }

    private Optional<String> validate() {
        Set<String> keys = new HashSet<>();
        for (PreviewRow row : rows) {
            if (!row.selected.get()) {
                continue;
            }
            if (row.name.get().isBlank()) {
                return Optional.of("Ein ausgewähltes Modul hat keinen Namen.");
            }
            if (!keys.add(ModuleEntry.keyOf(row.name.get()))) {
                return Optional.of("\"" + row.name.get() + "\" ist doppelt ausgewählt.");
            }
            ModuleEntry entry = row.toEntry();
            if (entry.getStatus() == ModuleStatus.BESTANDEN && entry.isGraded() && !Grades.isPassing(entry.getGrade())) {
                return Optional.of("\"" + entry.getName() + "\": Mit " + Grades.format(entry.getGrade())
                        + " ist das Modul nicht bestanden.");
            }
        }
        return Optional.empty();
    }

    /** Färbt "neu" / "geändert" / "unverändert" unterschiedlich ein. */
    private static final class StateCell extends TableCell<PreviewRow, ImportCandidate.State> {
        @Override
        protected void updateItem(ImportCandidate.State item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("state-new", "state-changed", "state-unchanged");
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(item.label());
            getStyleClass().add(switch (item) {
                case NEW -> "state-new";
                case CHANGED -> "state-changed";
                case UNCHANGED -> "state-unchanged";
            });
        }
    }

    /** Bearbeitbare Zeile der Vorschau; Zahlen als Text, damit man sie frei tippen kann. */
    private static final class PreviewRow {
        final Long id;
        final ImportCandidate.State state;
        final BooleanProperty selected;
        final StringProperty name;
        final StringProperty grade;
        final StringProperty ects;
        final StringProperty weight;
        final StringProperty semester;
        final ObjectProperty<ModuleStatus> status;
        final ObjectProperty<ModuleCategory> category;
        final BooleanProperty credited;
        final StringProperty remark;

        PreviewRow(ImportCandidate candidate) {
            ModuleEntry entry = candidate.entry();
            id = entry.getId();
            state = candidate.state();
            selected = new SimpleBooleanProperty(state != ImportCandidate.State.UNCHANGED);
            name = new SimpleStringProperty(entry.getName());
            grade = new SimpleStringProperty(entry.getGrade() == null ? "" : Grades.format(entry.getGrade()));
            ects = new SimpleStringProperty(Formats.number(entry.getEcts()));
            weight = new SimpleStringProperty(entry.getWeight() == null ? "" : Formats.number(entry.getWeight()));
            semester = new SimpleStringProperty(entry.getSemester() == null ? "" : entry.getSemester());
            status = new SimpleObjectProperty<>(entry.getStatus());
            category = new SimpleObjectProperty<>(entry.getCategory());
            credited = new SimpleBooleanProperty(entry.isCredited());
            remark = new SimpleStringProperty(entry.getRemark() == null ? "" : entry.getRemark());
        }

        ModuleEntry toEntry() {
            ModuleEntry entry = new ModuleEntry(
                    name.get().trim(),
                    Grades.parse(grade.get()).orElse(null),
                    Formats.parseNumber(ects.get()).orElse(0.0),
                    Formats.parseNumber(weight.get()).orElse(null),
                    semester.get().isBlank() ? null : semester.get().trim(),
                    status.get());
            entry.setId(id);
            entry.setCategory(category.get());
            entry.setCredited(credited.get());
            entry.setRemark(remark.get().isBlank() ? null : remark.get().trim());
            return entry;
        }
    }
}
