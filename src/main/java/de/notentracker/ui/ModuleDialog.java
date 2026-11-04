package de.notentracker.ui;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;
import de.notentracker.model.Semester;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.time.Year;
import java.util.Collection;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Dialog zum Anlegen und Bearbeiten eines Moduls. Liefert ein neues {@link ModuleEntry} oder nichts. */
class ModuleDialog extends Dialog<ModuleEntry> {

    private final TextField name = new TextField();
    private final TextField grade = new TextField();
    private final TextField ects = new TextField();
    private final TextField weight = new TextField();
    private final ComboBox<String> semester = new ComboBox<>();
    private final ComboBox<ModuleStatus> status = new ComboBox<>();
    private final ComboBox<ModuleCategory> category = new ComboBox<>();
    private final CheckBox credited = new CheckBox("aus anderem Studiengang angerechnet");
    private final TextField remark = new TextField();
    private final Label error = new Label();

    private final ModuleEntry original;
    private final Predicate<ModuleEntry> isDuplicate;

    /**
     * @param existing       zu bearbeitendes Modul oder {@code null} für ein neues
     * @param knownSemesters bereits verwendete Semester (für die Vorschlagsliste)
     * @param isDuplicate    prüft, ob es ein anderes Modul mit gleichem Namen gibt
     */
    ModuleDialog(ModuleEntry existing, Collection<String> knownSemesters, Predicate<ModuleEntry> isDuplicate) {
        this.original = existing;
        this.isDuplicate = isDuplicate;

        setTitle(existing == null ? "Modul hinzufügen" : "Modul bearbeiten");
        setHeaderText(null);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().getStyleClass().add("module-dialog");

        setUpFields(knownSemesters);
        if (existing != null) {
            fill(existing);
        }
        getDialogPane().setContent(buildForm());

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            Optional<String> problem = validate();
            if (problem.isPresent()) {
                error.setText(problem.get());
                event.consume();
            }
        });

        setResultConverter(button -> button == ButtonType.OK ? toEntry() : null);
        setOnShown(event -> name.requestFocus());
    }

    private void setUpFields(Collection<String> knownSemesters) {
        name.setPromptText("z. B. Datenbanken");
        grade.setPromptText("z. B. 2,3 – leer bei unbenotet/offen");
        ects.setPromptText("z. B. 5");
        weight.setPromptText("leer = zählt nicht zum Schnitt");
        remark.setPromptText("optional");

        int year = Year.now().getValue();
        TreeSet<String> suggestions = new TreeSet<>(Semester.ORDER);
        suggestions.addAll(Semester.range(year - 4, year + 1));
        knownSemesters.stream().filter(s -> s != null && !s.isBlank()).forEach(suggestions::add);
        semester.getItems().setAll(suggestions);
        semester.setEditable(true);
        semester.setPromptText("z. B. " + Semester.summer(year));
        semester.setMaxWidth(Double.MAX_VALUE);

        status.getItems().setAll(ModuleStatus.values());
        status.setValue(ModuleStatus.BESTANDEN);
        status.setMaxWidth(Double.MAX_VALUE);
        category.getItems().setAll(ModuleCategory.values());
        category.setValue(ModuleCategory.PFLICHT);
        category.setMaxWidth(Double.MAX_VALUE);

        // Eine eingegebene Note bestimmt den Status (4,0 und besser = bestanden).
        grade.textProperty().addListener((obs, old, text) ->
                Grades.parse(text).ifPresent(g -> status.setValue(ModuleStatus.fromGrade(g))));

        // Gewichtung folgt den ECTS, solange man sie nicht selbst geändert hat.
        ects.textProperty().addListener((obs, old, text) -> {
            if (category.getValue() != ModuleCategory.STUDIUM_GENERALE
                    && (weight.getText().isBlank() || weight.getText().equals(old))) {
                weight.setText(text);
            }
        });

        // Studium Generale geht nicht in den Schnitt ein.
        category.valueProperty().addListener((obs, old, value) -> {
            if (value == ModuleCategory.STUDIUM_GENERALE) {
                weight.clear();
            }
        });

        error.getStyleClass().add("form-error");
        error.setWrapText(true);
    }

    private GridPane buildForm() {
        GridPane form = new GridPane();
        form.getStyleClass().add("form");
        form.setHgap(12);
        form.setVgap(10);

        int row = 0;
        form.addRow(row++, new Label("Modul"), name);
        form.addRow(row++, new Label("Note"), grade);
        form.addRow(row++, new Label("ECTS"), ects);
        form.addRow(row++, new Label("Gewichtung"), weight);
        form.addRow(row++, new Label("Semester"), semester);
        form.addRow(row++, new Label("Status"), status);
        form.addRow(row++, new Label("Kategorie"), category);
        form.add(credited, 1, row++);
        form.addRow(row++, new Label("Bemerkung"), remark);
        form.add(error, 0, row, 2, 1);

        GridPane.setHgrow(name, Priority.ALWAYS);
        name.setPrefColumnCount(26);
        return form;
    }

    private void fill(ModuleEntry module) {
        name.setText(module.getName());
        ects.setText(Formats.number(module.getEcts()));
        // Gewichtung nach den ECTS setzen, damit der ECTS-Listener sie nicht überschreibt.
        weight.setText(module.getWeight() == null ? "" : Formats.number(module.getWeight()));
        category.setValue(module.getCategory());
        grade.setText(module.getGrade() == null ? "" : Grades.format(module.getGrade()));
        status.setValue(module.getStatus());
        semester.setValue(module.getSemester());
        credited.setSelected(module.isCredited());
        remark.setText(module.getRemark() == null ? "" : module.getRemark());
    }

    private Optional<String> validate() {
        if (name.getText().isBlank()) {
            return Optional.of("Bitte einen Modulnamen eingeben.");
        }
        if (!grade.getText().isBlank() && Grades.parse(grade.getText()).isEmpty()) {
            return Optional.of("Die Note muss zwischen 1,0 und 5,0 liegen.");
        }
        Optional<Double> ectsValue = Formats.parseNumber(ects.getText());
        if (ectsValue.isEmpty() || ectsValue.get() < 0) {
            return Optional.of("Bitte gültige ECTS eingeben (z. B. 5).");
        }
        if (!weight.getText().isBlank() && Formats.parseNumber(weight.getText()).filter(w -> w >= 0).isEmpty()) {
            return Optional.of("Die Gewichtung muss eine Zahl sein – oder leer bleiben.");
        }
        Optional<Double> gradeValue = Grades.parse(grade.getText());
        if (status.getValue() == ModuleStatus.BESTANDEN && gradeValue.filter(g -> !Grades.isPassing(g)).isPresent()) {
            return Optional.of("Mit einer " + Grades.format(gradeValue.get()) + " ist das Modul nicht bestanden.");
        }
        if (isDuplicate.test(toEntry())) {
            return Optional.of("Ein Modul mit diesem Namen gibt es schon.");
        }
        return Optional.empty();
    }

    private ModuleEntry toEntry() {
        String semesterText = semester.getEditor().getText();
        ModuleEntry entry = new ModuleEntry(
                name.getText().trim(),
                Grades.parse(grade.getText()).orElse(null),
                Formats.parseNumber(ects.getText()).orElse(0.0),
                Formats.parseNumber(weight.getText()).orElse(null),
                semesterText == null || semesterText.isBlank() ? null : semesterText.trim(),
                status.getValue());
        entry.setId(original == null ? null : original.getId());
        entry.setCategory(category.getValue());
        entry.setCredited(credited.isSelected());
        entry.setRemark(remark.getText().isBlank() ? null : remark.getText().trim());
        return entry;
    }
}
