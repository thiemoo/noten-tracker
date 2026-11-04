package de.notentracker.ui;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleEntry;
import de.notentracker.service.GradeCalculator;
import de.notentracker.service.GradeStatistics;
import de.notentracker.service.TargetGradeResult;
import javafx.beans.property.DoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** Zielschnitt-Rechner: Welchen Schnitt brauche ich in den restlichen Modulen für meinen Wunschschnitt? */
class TargetCalculatorPane extends VBox {

    private static final PseudoClass GOOD = PseudoClass.getPseudoClass("good");
    private static final PseudoClass BAD = PseudoClass.getPseudoClass("bad");

    private final ObservableList<ModuleEntry> modules;
    private final GradeCalculator calculator;
    private final DoubleProperty totalEcts;
    private final DoubleProperty targetAverage;

    private final TextField targetField = new TextField();
    private final TextField totalEctsField = new TextField();
    private final CheckBox automatic = new CheckBox("automatisch berechnen");
    private final TextField remainingField = new TextField();
    private final Label remainingHint = new Label();

    private final Label resultValue = new Label();
    private final Label resultText = new Label();
    private final Label basisText = new Label();

    TargetCalculatorPane(ObservableList<ModuleEntry> modules, GradeCalculator calculator,
                         DoubleProperty totalEcts, DoubleProperty targetAverage) {
        this.modules = modules;
        this.calculator = calculator;
        this.totalEcts = totalEcts;
        this.targetAverage = targetAverage;

        getStyleClass().add("page");
        setSpacing(16);

        targetField.setText(Formats.number(targetAverage.get()));
        totalEctsField.setText(Formats.number(totalEcts.get()));
        automatic.setSelected(true);
        remainingField.disableProperty().bind(automatic.selectedProperty());
        remainingHint.getStyleClass().add("hint");
        remainingHint.setWrapText(true);

        targetField.textProperty().addListener((obs, old, text) -> {
            parseTarget(text).ifPresent(targetAverage::set);
            recalculate();
        });
        totalEctsField.textProperty().addListener((obs, old, text) -> {
            Formats.parseNumber(text).filter(v -> v > 0).ifPresent(totalEcts::set);
            recalculate();
        });
        automatic.selectedProperty().addListener((obs, old, value) -> recalculate());
        remainingField.textProperty().addListener((obs, old, text) -> {
            if (!automatic.isSelected()) {
                recalculate();
            }
        });
        modules.addListener((ListChangeListener<ModuleEntry>) change -> recalculate());

        GridPane inputs = new GridPane();
        inputs.getStyleClass().add("form");
        inputs.setHgap(12);
        inputs.setVgap(10);
        inputs.addRow(0, new Label("Wunschschnitt"), targetField);
        inputs.addRow(1, new Label("ECTS für den Abschluss"), totalEctsField);
        inputs.addRow(2, new Label("Noch offene Gewichtung"), remainingField, automatic);
        inputs.add(remainingHint, 1, 3, 2, 1);

        Label inputTitle = new Label("Zielschnitt-Rechner");
        inputTitle.getStyleClass().add("card-title");
        VBox inputCard = new VBox(12, inputTitle, inputs);
        inputCard.getStyleClass().add("card");

        Label resultTitle = new Label("Benötigter Schnitt in den restlichen Modulen");
        resultTitle.getStyleClass().add("kpi-title");
        resultValue.getStyleClass().add("result-value");
        resultText.setWrapText(true);
        basisText.getStyleClass().add("hint");
        basisText.setWrapText(true);
        VBox resultCard = new VBox(6, resultTitle, resultValue, resultText, basisText);
        resultCard.getStyleClass().add("card");

        getChildren().addAll(inputCard, resultCard);
        recalculate();
    }

    private void recalculate() {
        GradeStatistics stats = calculator.statistics(modules);
        double openWeight = calculator.openWeight(modules);

        if (automatic.isSelected()) {
            // Mit importierten offenen Modulen ist deren Gewichtung genau; sonst grob: Rest-ECTS bis zum Abschluss.
            double estimate = openWeight > 0 ? openWeight : Math.max(0, totalEcts.get() - stats.earnedEcts());
            remainingField.setText(Formats.number(estimate));
            remainingHint.setText(openWeight > 0
                    ? "Summe der Gewichtungen aller noch nicht bestandenen Module in deiner Liste. "
                    + "Fehlen dort noch Module, trag den Wert selbst ein."
                    : "Geschätzt: ECTS für den Abschluss minus bereits erreichte ECTS.");
        } else {
            remainingHint.setText("Eigener Wert, z. B. wenn unbenotete Module noch offen sind.");
        }

        Optional<Double> target = parseTarget(targetField.getText());
        Optional<Double> remaining = Formats.parseNumber(remainingField.getText()).filter(v -> v >= 0);
        if (target.isEmpty() || remaining.isEmpty()) {
            show("–", "Bitte einen Wunschschnitt zwischen 1,0 und 5,0 und eine gültige Gewichtung eingeben.", null);
            basisText.setText("");
            return;
        }

        TargetGradeResult result = calculator.requiredAverage(modules, target.get(), remaining.get());
        String goal = Formats.number(target.get());
        String rest = Formats.number(result.remainingWeight());
        switch (result.outcome()) {
            case ACHIEVABLE -> show(Formats.average(result.requiredAverage()),
                    "Für einen Schnitt von " + goal + " brauchst du in den restlichen " + rest
                            + " gewichteten ECTS im Mittel " + Formats.average(result.requiredAverage()) + " oder besser.",
                    null);
            case ALREADY_SECURE -> show("✓ sicher",
                    "Selbst mit lauter 4,0 in den restlichen " + rest + " gewichteten ECTS erreichst du " + goal + ".",
                    true);
            case NOT_ACHIEVABLE -> show("nicht erreichbar",
                    "Selbst mit lauter 1,0 in den restlichen " + rest + " gewichteten ECTS reicht es nicht für "
                            + goal + ". Probier ein etwas höheres Ziel.",
                    false);
            case REACHED -> show("✓ erreicht", "Alles abgeschlossen – dein Schnitt liegt bei " + goal + " oder besser.", true);
            case MISSED -> show("verfehlt", "Alles abgeschlossen – der Schnitt liegt über " + goal + ".", false);
        }

        basisText.setText(stats.weightedAverage().isPresent()
                ? "Aktuell: " + Formats.average(stats.weightedAverage().getAsDouble()) + " über "
                + Formats.number(stats.gradedWeight()) + " gewichtete ECTS."
                : "Noch keine benoteten Module – der Rechner geht von deinem Wunschschnitt aus.");
    }

    /** Wunschschnitt darf auch Zwischenwerte wie 2,45 haben – anders als eine Einzelnote. */
    private static Optional<Double> parseTarget(String text) {
        return Formats.parseNumber(text).filter(v -> v >= Grades.BEST && v <= Grades.WORST);
    }

    /** @param good {@code true} grün, {@code false} rot, {@code null} neutral */
    private void show(String value, String text, Boolean good) {
        resultValue.setText(value);
        resultText.setText(text);
        resultValue.pseudoClassStateChanged(GOOD, Boolean.TRUE.equals(good));
        resultValue.pseudoClassStateChanged(BAD, Boolean.FALSE.equals(good));
    }
}
