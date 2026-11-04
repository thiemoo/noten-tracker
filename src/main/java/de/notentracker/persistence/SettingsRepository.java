package de.notentracker.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Einfache Schlüssel/Wert-Einstellungen, z. B. die ECTS für den Abschluss oder der Wunschschnitt. */
public class SettingsRepository {

    public static final String TOTAL_ECTS = "total_ects";
    public static final String TARGET_AVERAGE = "target_average";
    public static final String THEME = "theme";

    public static final double DEFAULT_TOTAL_ECTS = 210;

    private final Database database;

    public SettingsRepository(Database database) {
        this.database = database;
    }

    public Optional<String> get(String key) {
        try (Connection connection = database.connect();
             PreparedStatement ps = connection.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Einstellung \"" + key + "\" kann nicht gelesen werden", e);
        }
    }

    public double getDouble(String key, double defaultValue) {
        return get(key).map(Double::parseDouble).orElse(defaultValue);
    }

    public void set(String key, String value) {
        try (Connection connection = database.connect();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO settings (key, value) VALUES (?, ?)
                     ON CONFLICT(key) DO UPDATE SET value = excluded.value""")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Einstellung \"" + key + "\" kann nicht gespeichert werden", e);
        }
    }

    public void setDouble(String key, double value) {
        set(key, Double.toString(value));
    }
}
