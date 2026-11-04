package de.notentracker;

import de.notentracker.persistence.DataAccessException;
import de.notentracker.persistence.Database;
import de.notentracker.persistence.ModuleRepository;
import de.notentracker.persistence.SettingsRepository;
import de.notentracker.service.GradeCalculator;
import de.notentracker.service.ImportService;
import de.notentracker.ui.MainView;
import de.notentracker.ui.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

/** JavaFX-Anwendung: öffnet die Datenbank und zeigt das Hauptfenster. */
public class NotenTrackerApp extends Application {

    @Override
    public void start(Stage stage) {
        MainView view;
        ThemeManager theme;
        try {
            Database database = new Database(Database.defaultLocation());
            ModuleRepository modules = new ModuleRepository(database);
            SettingsRepository settings = new SettingsRepository(database);
            theme = new ThemeManager(settings);
            view = new MainView(modules, settings, new GradeCalculator(), new ImportService(modules), theme);
        } catch (DataAccessException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
            alert.setHeaderText("Die Datenbank konnte nicht geöffnet werden");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        Scene scene = new Scene(view.getRoot(), 1320, 820);
        scene.getStylesheets().add(ThemeManager.STYLESHEET);
        theme.register(scene.getRoot());
        stage.setScene(scene);
        stage.setTitle("Noten-Tracker");
        stage.getIcons().add(new Image(
                Objects.requireNonNull(getClass().getResource("/de/notentracker/ui/icon.png")).toExternalForm()));
        stage.setMinWidth(1080);
        stage.setMinHeight(680);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
