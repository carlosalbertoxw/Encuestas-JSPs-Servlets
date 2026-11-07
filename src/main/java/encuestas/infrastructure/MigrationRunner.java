package encuestas.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;

/**
 * Aplica al arrancar los scripts SQL de src/main/resources/db/migration (en orden por nombre)
 * y registra cada uno en la tabla {@code schemaversions}. Los scripts se descubren solos: para
 * evolucionar el esquema basta con agregar un archivo {@code V0003__descripcion.sql}. Nunca
 * edites uno ya aplicado.
 */
public final class MigrationRunner {

    private static final Logger LOGGER = Logger.getLogger(MigrationRunner.class.getName());

    private static final String LOCATION = "db/migration";
    private static final Pattern SCRIPT_NAME = Pattern.compile("V\\d+__\\w+\\.sql");

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

            for (String script : discoverScripts()) {
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

    /**
     * Nombres de los scripts de la carpeta de migraciones, ordenados. Funciona tanto si las
     * clases están en un directorio (WAR desplegado, pruebas) como dentro de un JAR.
     */
    static List<String> discoverScripts() {
        URL url = MigrationRunner.class.getClassLoader().getResource(LOCATION);
        if (url == null) {
            throw new IllegalStateException("No se encontró la carpeta de migraciones " + LOCATION);
        }
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem zip = openZip(uri)) {
                    return listScripts(zip.provider().getPath(uri));
                }
            }
            return listScripts(Path.of(uri));
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("No se pudieron listar las migraciones", e);
        }
    }

    private static FileSystem openZip(URI uri) throws IOException {
        try {
            return FileSystems.newFileSystem(uri, Map.of());
        } catch (FileSystemAlreadyExistsException e) {
            return FileSystems.getFileSystem(uri);
        }
    }

    private static List<String> listScripts(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> String.valueOf(path.getFileName()))
                    .filter(name -> SCRIPT_NAME.matcher(name).matches())
                    .sorted()
                    .toList();
        }
    }

    private static String readScript(String name) {
        String resource = "/" + LOCATION + "/" + name;
        try (var stream = MigrationRunner.class.getResourceAsStream(resource)) {
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
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .toArray(String[]::new);
    }

    private MigrationRunner() {
    }
}
