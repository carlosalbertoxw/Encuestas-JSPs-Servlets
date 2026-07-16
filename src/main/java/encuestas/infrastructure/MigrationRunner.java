package encuestas.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Aplica los scripts SQL versionados de src/main/resources/db/migration al arrancar y
 * registra cada uno en la tabla {@code schemaversions}. Para evolucionar el esquema,
 * agrega un script nuevo a {@link #SCRIPTS} — nunca edites uno ya aplicado.
 */
public final class MigrationRunner {

    private static final Logger LOGGER = Logger.getLogger(MigrationRunner.class.getName());

    private static final String[] SCRIPTS = {
            "V0001__esquema_inicial.sql"
    };

    public static void run(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schemaversions ("
                        + "schemaversionsid INT NOT NULL AUTO_INCREMENT, "
                        + "scriptname VARCHAR(255) NOT NULL, "
                        + "applied DATETIME NOT NULL, "
                        + "PRIMARY KEY (schemaversionsid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }

            Set<String> applied = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT scriptname FROM schemaversions")) {
                while (rs.next()) {
                    applied.add(rs.getString(1));
                }
            }

            for (String script : SCRIPTS) {
                if (applied.contains(script)) {
                    continue;
                }
                LOGGER.log(Level.INFO, "Aplicando migración {0}", script);
                for (String sql : splitStatements(readScript(script))) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO schemaversions(scriptname, applied) VALUES(?, UTC_TIMESTAMP())")) {
                    statement.setString(1, script);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al aplicar las migraciones de la base de datos", e);
        }
    }

    private static String readScript(String name) {
        String resource = "/db/migration/" + name;
        try (InputStream stream = MigrationRunner.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("No se encontró el script " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el script " + resource, e);
        }
    }

    /** Divide el script en sentencias; suficiente para DDL sin procedimientos almacenados. */
    private static String[] splitStatements(String script) {
        // Elimina los comentarios de línea para que un ';' dentro de un comentario no parta mal.
        String withoutComments = script.replaceAll("(?m)^\\s*--.*$", "");
        return java.util.Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .toArray(String[]::new);
    }

    private MigrationRunner() {
    }
}
