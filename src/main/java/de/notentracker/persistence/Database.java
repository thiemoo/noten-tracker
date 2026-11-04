package de.notentracker.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Zugriff auf die lokale SQLite-Datei. Legt beim ersten Start das Schema an und
 * migriert es über {@code PRAGMA user_version}, wenn später Spalten dazukommen.
 */
public class Database {

    /** Aktuelle Schema-Version. Bei Änderungen erhöhen und in {@link #migrate} einen Schritt ergänzen. */
    private static final int SCHEMA_VERSION = 1;

    private final String url;

    public Database(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Datenbank-Ordner kann nicht angelegt werden: " + file, e);
        }
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        migrate();
    }

    /**
     * Standard-Speicherort: unter Windows {@code %APPDATA%\NotenTracker\noten.db},
     * sonst {@code ~/.noten-tracker/noten.db}. Mit {@code -Dnotentracker.db=<pfad>} lässt er sich
     * überschreiben, z. B. für eine Testdatenbank.
     */
    public static Path defaultLocation() {
        String override = System.getProperty("notentracker.db");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appData = System.getenv("APPDATA");
        Path dir = appData != null
                ? Path.of(appData, "NotenTracker")
                : Path.of(System.getProperty("user.home"), ".noten-tracker");
        return dir.resolve("noten.db");
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void migrate() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            int version;
            try (ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
                version = rs.next() ? rs.getInt(1) : 0;
            }
            if (version < 1) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS modules (
                            id        INTEGER PRIMARY KEY AUTOINCREMENT,
                            name      TEXT    NOT NULL,
                            name_key  TEXT    NOT NULL UNIQUE,
                            grade     REAL,
                            ects      REAL    NOT NULL DEFAULT 0,
                            weight    REAL,
                            semester  TEXT,
                            status    TEXT    NOT NULL,
                            category  TEXT    NOT NULL,
                            credited  INTEGER NOT NULL DEFAULT 0,
                            remark    TEXT
                        )""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS settings (
                            key   TEXT PRIMARY KEY,
                            value TEXT NOT NULL
                        )""");
            }
            // Künftige Migrationen: if (version < 2) { ALTER TABLE ... }
            if (version < SCHEMA_VERSION) {
                statement.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Datenbank kann nicht initialisiert werden: " + url, e);
        }
    }
}
