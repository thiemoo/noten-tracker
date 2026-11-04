package de.notentracker.persistence;

import de.notentracker.model.ModuleCategory;
import de.notentracker.model.ModuleEntry;
import de.notentracker.model.ModuleStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Speichert, lädt und löscht Module in der Tabelle {@code modules}. */
public class ModuleRepository {

    private static final String COLUMNS =
            "name, name_key, grade, ects, weight, semester, status, category, credited, remark";

    private final Database database;

    public ModuleRepository(Database database) {
        this.database = database;
    }

    public List<ModuleEntry> findAll() {
        try (Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id, " + COLUMNS + " FROM modules ORDER BY id")) {
            List<ModuleEntry> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Module können nicht geladen werden", e);
        }
    }

    /** Sucht ein Modul über den Duplikat-Schlüssel (normalisierter Name). */
    public Optional<ModuleEntry> findByKey(String key) {
        try (Connection connection = database.connect()) {
            return findByKey(connection, key);
        } catch (SQLException e) {
            throw new DataAccessException("Modul kann nicht gesucht werden", e);
        }
    }

    /** Legt ein neues Modul an und setzt dessen ID. Wirft eine Exception, wenn der Name schon existiert. */
    public void insert(ModuleEntry module) {
        try (Connection connection = database.connect()) {
            insert(connection, module);
        } catch (SQLException e) {
            throw new DataAccessException("Modul \"" + module.getName() + "\" kann nicht gespeichert werden", e);
        }
    }

    public void update(ModuleEntry module) {
        try (Connection connection = database.connect()) {
            update(connection, module);
        } catch (SQLException e) {
            throw new DataAccessException("Modul \"" + module.getName() + "\" kann nicht aktualisiert werden", e);
        }
    }

    public void delete(long id) {
        try (Connection connection = database.connect();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM modules WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Modul kann nicht gelöscht werden", e);
        }
    }

    /**
     * Übernimmt importierte Module in einer Transaktion: neue Module werden angelegt, bekannte (gleicher Name)
     * aktualisiert, unveränderte übersprungen. So entstehen beim erneuten Import keine Duplikate.
     */
    public ImportResult upsertAll(List<ModuleEntry> modules) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                int inserted = 0;
                int updated = 0;
                int unchanged = 0;
                for (ModuleEntry module : modules) {
                    Optional<ModuleEntry> existing = findByKey(connection, module.duplicateKey());
                    if (existing.isEmpty()) {
                        insert(connection, module);
                        inserted++;
                    } else if (existing.get().hasSameContent(module)) {
                        module.setId(existing.get().getId());
                        unchanged++;
                    } else {
                        module.setId(existing.get().getId());
                        update(connection, module);
                        updated++;
                    }
                }
                connection.commit();
                return new ImportResult(inserted, updated, unchanged, 0);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Import kann nicht gespeichert werden", e);
        }
    }

    private Optional<ModuleEntry> findByKey(Connection connection, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, " + COLUMNS + " FROM modules WHERE name_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private void insert(Connection connection, ModuleEntry module) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO modules (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, module);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    module.setId(keys.getLong(1));
                }
            }
        }
    }

    private void update(Connection connection, ModuleEntry module) throws SQLException {
        if (module.getId() == null) {
            throw new IllegalArgumentException("Modul ohne ID kann nicht aktualisiert werden: " + module.getName());
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE modules SET name = ?, name_key = ?, grade = ?, ects = ?, weight = ?, semester = ?,
                                   status = ?, category = ?, credited = ?, remark = ?
                WHERE id = ?""")) {
            bind(ps, module);
            ps.setLong(11, module.getId());
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, ModuleEntry module) throws SQLException {
        ps.setString(1, module.getName());
        ps.setString(2, module.duplicateKey());
        setNullableDouble(ps, 3, module.getGrade());
        ps.setDouble(4, module.getEcts());
        setNullableDouble(ps, 5, module.getWeight());
        ps.setString(6, module.getSemester());
        ps.setString(7, module.getStatus().name());
        ps.setString(8, module.getCategory().name());
        ps.setInt(9, module.isCredited() ? 1 : 0);
        ps.setString(10, module.getRemark());
    }

    private static ModuleEntry map(ResultSet rs) throws SQLException {
        ModuleEntry module = new ModuleEntry(
                rs.getString("name"),
                getNullableDouble(rs, "grade"),
                rs.getDouble("ects"),
                getNullableDouble(rs, "weight"),
                rs.getString("semester"),
                ModuleStatus.valueOf(rs.getString("status")));
        module.setId(rs.getLong("id"));
        module.setCategory(ModuleCategory.valueOf(rs.getString("category")));
        module.setCredited(rs.getInt("credited") != 0);
        module.setRemark(rs.getString("remark"));
        return module;
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
