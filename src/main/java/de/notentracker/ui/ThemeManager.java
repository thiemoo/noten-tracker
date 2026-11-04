package de.notentracker.ui;

import de.notentracker.persistence.SettingsRepository;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Hell/Dunkel-Umschaltung. Alle Farben stehen als Variablen in {@code styles.css}; im dunklen Modus bekommt
 * die Wurzel jedes Fensters die Style-Klasse {@code dark}, die diese Variablen überschreibt.
 */
public class ThemeManager {

    public enum Mode {
        LIGHT("Hell"), DARK("Dunkel"), SYSTEM("System");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final String STYLESHEET = Objects.requireNonNull(
            ThemeManager.class.getResource("/de/notentracker/ui/styles.css")).toExternalForm();
    private static final String DARK_CLASS = "dark";

    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>();
    private final Set<Parent> roots = new HashSet<>();

    public ThemeManager(SettingsRepository settings) {
        Mode saved = settings.get(SettingsRepository.THEME)
                .flatMap(ThemeManager::parse)
                .orElse(Mode.SYSTEM);
        mode.set(saved);
        mode.addListener((obs, old, value) -> {
            settings.set(SettingsRepository.THEME, value.name());
            applyAll();
        });
        // Bei "System" sofort mitziehen, wenn Windows zwischen hell und dunkel wechselt.
        Platform.getPreferences().colorSchemeProperty().addListener((obs, old, value) -> applyAll());
    }

    private static Optional<Mode> parse(String text) {
        try {
            return Optional.of(Mode.valueOf(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    public boolean isDark() {
        return switch (mode.get()) {
            case LIGHT -> false;
            case DARK -> true;
            case SYSTEM -> Platform.getPreferences().getColorScheme() == ColorScheme.DARK;
        };
    }

    /** Fenster-Wurzel anmelden, damit sie beim Umschalten mitgeht. */
    public void register(Parent root) {
        roots.add(root);
        apply(root);
    }

    /** Dialoge bekommen Stylesheet und Modus; nach dem Schließen werden sie wieder abgemeldet. */
    public void style(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStylesheets().contains(STYLESHEET)) {
            pane.getStylesheets().add(STYLESHEET);
        }
        register(pane);
        dialog.setOnHidden(event -> roots.remove(pane));
    }

    private void applyAll() {
        roots.forEach(this::apply);
    }

    private void apply(Parent root) {
        boolean dark = isDark();
        if (dark && !root.getStyleClass().contains(DARK_CLASS)) {
            root.getStyleClass().add(DARK_CLASS);
        } else if (!dark) {
            root.getStyleClass().remove(DARK_CLASS);
        }
    }
}
