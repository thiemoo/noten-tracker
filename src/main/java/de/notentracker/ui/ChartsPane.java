package de.notentracker.ui;

import de.notentracker.model.Grades;
import de.notentracker.model.ModuleEntry;
import de.notentracker.service.ChartData;
import de.notentracker.service.GradeCalculator;
import de.notentracker.service.GradeStatistics;
import de.notentracker.service.SemesterPoint;
import javafx.beans.value.ObservableDoubleValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Map;

/**
 * Reiter "Diagramme": Notenverlauf, Notenverteilung und ECTS-Fortschritt.
 * Die genauen Werte stehen zusätzlich in der Tabelle der Übersicht; Tooltips ergänzen nur.
 */
class ChartsPane extends VBox {

    private final ObservableList<ModuleEntry> modules;
    private final ObservableDoubleValue totalEcts;
    private final GradeCalculator calculator;
    private final ChartData chartData;

    private final NumberAxis gradeAxis = new NumberAxis(-Grades.WORST, -Grades.BEST, 0.5);
    private final LineChart<String, Number> trendChart = new LineChart<>(new CategoryAxis(), gradeAxis);
    private final BarChart<String, Number> distributionChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final NumberAxis ectsAxis = new NumberAxis();
    private final AreaChart<String, Number> ectsChart = new AreaChart<>(new CategoryAxis(), ectsAxis);

    private final Label trendSubtitle = new Label();
    private final Label distributionSubtitle = new Label();
    private final Label ectsSubtitle = new Label();
    private final Label trendEmpty = emptyLabel("Noch keine benoteten Module mit Semester.");
    private final Label distributionEmpty = emptyLabel("Noch keine benoteten Module.");
    private final Label ectsEmpty = emptyLabel("Noch keine bestandenen Module mit Semester.");

    ChartsPane(ObservableList<ModuleEntry> modules, GradeCalculator calculator, ObservableDoubleValue totalEcts) {
        this.modules = modules;
        this.totalEcts = totalEcts;
        this.calculator = calculator;
        this.chartData = new ChartData(calculator);

        getStyleClass().add("page");
        setSpacing(16);

        setUpTrendChart();
        setUpDistributionChart();
        setUpEctsChart();

        VBox trendCard = card("Notenverlauf", trendSubtitle, trendChart, trendEmpty);
        VBox distributionCard = card("Notenverteilung", distributionSubtitle, distributionChart, distributionEmpty);
        HBox top = new HBox(16, trendCard, distributionCard);
        HBox.setHgrow(trendCard, Priority.ALWAYS);
        HBox.setHgrow(distributionCard, Priority.ALWAYS);
        trendCard.setPrefWidth(0);
        distributionCard.setPrefWidth(0);
        VBox ectsCard = card("ECTS-Fortschritt", ectsSubtitle, ectsChart, ectsEmpty);
        VBox.setVgrow(top, Priority.ALWAYS);
        VBox.setVgrow(ectsCard, Priority.ALWAYS);
        getChildren().addAll(top, ectsCard);

        modules.addListener((ListChangeListener<ModuleEntry>) change -> refresh());
        totalEcts.addListener((obs, old, value) -> refresh());
        refresh();
    }

    private static Label emptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        return label;
    }

    private static VBox card(String title, Label subtitle, Node chart, Label empty) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        subtitle.getStyleClass().add("hint");
        StackPane body = new StackPane(chart, empty);
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox card = new VBox(4, titleLabel, subtitle, body);
        card.getStyleClass().addAll("card", "chart-card");
        return card;
    }

    private void setUpTrendChart() {
        // Negativ aufgetragen, damit 1,0 oben steht – bei Noten ist kleiner besser.
        gradeAxis.setAutoRanging(false);
        gradeAxis.setMinorTickVisible(false);
        gradeAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return Grades.format(-value.doubleValue());
            }

            @Override
            public Number fromString(String text) {
                return -Double.parseDouble(text.replace(',', '.'));
            }
        });
        trendChart.setAnimated(false);
        trendChart.setLegendVisible(true);
        trendChart.setMinHeight(260);
    }

    private void setUpDistributionChart() {
        NumberAxis countAxis = (NumberAxis) distributionChart.getYAxis();
        countAxis.setTickUnit(1);
        countAxis.setMinorTickVisible(false);
        countAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                // nur ganze Zahlen beschriften – "1,5 Module" gibt es nicht
                return value.doubleValue() % 1 == 0 ? String.valueOf(value.intValue()) : "";
            }

            @Override
            public Number fromString(String text) {
                return Integer.parseInt(text);
            }
        });
        distributionChart.setAnimated(false);
        distributionChart.setLegendVisible(false);
        distributionChart.setCategoryGap(6);
        distributionChart.setMinHeight(260);
    }

    private void setUpEctsChart() {
        ectsAxis.setAutoRanging(false);
        ectsAxis.setLowerBound(0);
        ectsAxis.setMinorTickVisible(false);
        ectsChart.setAnimated(false);
        ectsChart.setLegendVisible(false);
        ectsChart.setCreateSymbols(true);
        ectsChart.setMinHeight(220);
    }

    private void refresh() {
        List<SemesterPoint> trend = chartData.semesterTrend(modules);
        // Semester fest vorgeben, sonst ordnet die Achse nach erstem Auftreten in irgendeiner Reihe.
        ObservableList<String> semesters = FXCollections.observableArrayList(
                trend.stream().map(SemesterPoint::semester).toList());
        for (CategoryAxis axis : List.of((CategoryAxis) trendChart.getXAxis(), (CategoryAxis) ectsChart.getXAxis())) {
            axis.setAutoRanging(false);
            axis.setCategories(semesters);
        }
        refreshTrend(trend);
        refreshDistribution();
        refreshEcts(trend);
    }

    private void refreshTrend(List<SemesterPoint> trend) {
        XYChart.Series<String, Number> perSemester = new XYChart.Series<>();
        perSemester.setName("Semesterschnitt");
        XYChart.Series<String, Number> cumulative = new XYChart.Series<>();
        cumulative.setName("Gesamtschnitt");

        for (SemesterPoint point : trend) {
            point.semesterAverage().ifPresent(avg -> perSemester.getData().add(
                    point(point.semester(), -avg, point.semester() + "\nSemesterschnitt: " + Formats.average(avg))));
            point.cumulativeAverage().ifPresent(avg -> cumulative.getData().add(
                    point(point.semester(), -avg, point.semester() + "\nGesamtschnitt bis hier: " + Formats.average(avg))));
        }
        trendChart.getData().setAll(List.of(perSemester, cumulative));

        boolean empty = cumulative.getData().isEmpty();
        trendChart.setVisible(!empty);
        trendEmpty.setVisible(empty);

        GradeStatistics stats = calculator.statistics(modules);
        trendSubtitle.setText(stats.weightedAverage().isPresent()
                ? "Gewichteter Schnitt je Semester · aktuell gesamt " + Formats.average(stats.weightedAverage().getAsDouble())
                + " · oben = besser"
                : "Gewichteter Schnitt je Semester · oben = besser");
    }

    private void refreshDistribution() {
        Map<Double, List<String>> distribution = chartData.gradeDistribution(modules);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Anzahl Module");
        int total = 0;
        int max = 0;
        for (Map.Entry<Double, List<String>> entry : distribution.entrySet()) {
            int count = entry.getValue().size();
            total += count;
            max = Math.max(max, count);
            String tooltip = Grades.format(entry.getKey()) + ": " + count + (count == 1 ? " Modul" : " Module")
                    + (count > 0 ? "\n" + String.join("\n", entry.getValue()) : "");
            series.getData().add(point(Grades.format(entry.getKey()), count, tooltip));
        }
        distributionChart.getData().setAll(List.of(series));
        NumberAxis countAxis = (NumberAxis) distributionChart.getYAxis();
        countAxis.setAutoRanging(false);
        countAxis.setLowerBound(0);
        countAxis.setUpperBound(Math.max(1, max + 1));

        distributionChart.setVisible(total > 0);
        distributionEmpty.setVisible(total == 0);
        distributionSubtitle.setText(total + " benotete Prüfungen (inkl. nicht bestandener)");
    }

    private void refreshEcts(List<SemesterPoint> trend) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("ECTS");
        for (SemesterPoint point : trend) {
            series.getData().add(point(point.semester(), point.cumulativeEcts(),
                    point.semester() + "\n+" + Formats.number(point.earnedEcts()) + " ECTS · gesamt "
                            + Formats.number(point.cumulativeEcts())));
        }
        ectsChart.getData().setAll(List.of(series));

        double total = totalEcts.get();
        double earned = trend.isEmpty() ? 0 : trend.get(trend.size() - 1).cumulativeEcts();
        ectsAxis.setUpperBound(Math.max(total, earned));
        ectsAxis.setTickUnit(total >= 120 ? 30 : 10);

        ectsChart.setVisible(!trend.isEmpty());
        ectsEmpty.setVisible(trend.isEmpty());
        double earnedAll = calculator.statistics(modules).earnedEcts();
        ectsSubtitle.setText(Formats.number(earnedAll) + " von " + Formats.number(total) + " ECTS ("
                + Math.round(total > 0 ? earnedAll / total * 100 : 0) + " %) · Achse endet beim Abschluss");
    }

    /** Datenpunkt mit Tooltip; der Knoten entsteht erst beim Rendern, daher über den Listener. */
    private static XYChart.Data<String, Number> point(String x, Number y, String tooltipText) {
        XYChart.Data<String, Number> data = new XYChart.Data<>(x, y);
        data.nodeProperty().addListener((obs, old, node) -> {
            if (node != null) {
                Tooltip tooltip = new Tooltip(tooltipText);
                tooltip.setShowDelay(Duration.millis(80));
                Tooltip.install(node, tooltip);
            }
        });
        return data;
    }
}
