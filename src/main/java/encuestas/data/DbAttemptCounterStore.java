package encuestas.data;

import encuestas.service.AttemptCounterStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;

/**
 * Contadores en la tabla {@code a_attempt_counters}. El incremento es una sola sentencia
 * atómica (INSERT … ON DUPLICATE KEY UPDATE), así que dos réplicas no pierden intentos.
 * {@code DatabaseCleanup} borra las filas caducadas.
 */
public class DbAttemptCounterStore implements AttemptCounterStore {

    private final DataSource dataSource;

    public DbAttemptCounterStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int count(String key, Duration window) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT ac_count FROM a_attempt_counters "
                             + "WHERE ac_key=? AND ac_window_start > UTC_TIMESTAMP() - INTERVAL ? SECOND")) {
            statement.setString(1, key);
            statement.setLong(2, window.toSeconds());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer el contador de intentos", e);
        }
    }

    @Override
    public void increment(String key, Duration window) {
        // MySQL evalúa las asignaciones en orden: ac_count se calcula con la ventana anterior.
        String sql = "INSERT INTO a_attempt_counters(ac_key, ac_count, ac_window_start) VALUES(?, 1, UTC_TIMESTAMP()) "
                + "ON DUPLICATE KEY UPDATE "
                + "ac_count = IF(ac_window_start > UTC_TIMESTAMP() - INTERVAL ? SECOND, ac_count + 1, 1), "
                + "ac_window_start = IF(ac_window_start > UTC_TIMESTAMP() - INTERVAL ? SECOND, "
                + "ac_window_start, UTC_TIMESTAMP())";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setLong(2, window.toSeconds());
            statement.setLong(3, window.toSeconds());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar el intento", e);
        }
    }

    @Override
    public void reset(String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM a_attempt_counters WHERE ac_key=?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo reiniciar el contador de intentos", e);
        }
    }
}
