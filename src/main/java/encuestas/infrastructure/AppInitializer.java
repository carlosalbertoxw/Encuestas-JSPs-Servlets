package encuestas.infrastructure;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de arranque de la aplicación: construye los servicios, aplica las migraciones
 * pendientes antes de aceptar tráfico y, en Development, inserta los datos de demostración.
 */
@WebListener
public class AppInitializer implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AppInitializer.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        AppConfig config = AppConfig.fromEnvironment();
        App app = App.start(config);
        MigrationRunner.run(app.getDatabase().getDataSource());
        if (config.isDevelopment()) {
            DevDataSeeder.seed(app);
        }
        // Fuera de Development la cookie de sesión solo viaja por HTTPS (los navegadores
        // tratan a localhost como origen seguro, así que el uso local sigue funcionando).
        event.getServletContext().getSessionCookieConfig().setSecure(!config.isDevelopment());
        // Versión de los assets derivada de su contenido, para el cache busting (?v=...).
        event.getServletContext().setAttribute("assetsVersion",
                computeAssetsVersion(event.getServletContext()));
        LOGGER.log(Level.INFO, "Aplicación iniciada (entorno {0})",
                config.isDevelopment() ? "Development" : "Production");
    }

    /**
     * Hash del contenido de los archivos estáticos: si un asset cambia, cambian las URL
     * con las que las vistas los referencian y los navegadores no sirven caché vieja.
     */
    private static String computeAssetsVersion(ServletContext context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> paths = new ArrayList<>();
            collectPaths(context, "/assets/", paths);
            paths.add("/favicon.ico");
            paths.sort(String::compareTo);
            for (String path : paths) {
                try (InputStream stream = context.getResourceAsStream(path)) {
                    if (stream != null) {
                        digest.update(stream.readAllBytes());
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        } catch (NoSuchAlgorithmException | IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo calcular la versión de los assets", e);
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private static void collectPaths(ServletContext context, String dir, List<String> paths) {
        Set<String> entries = context.getResourcePaths(dir);
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry.endsWith("/")) {
                collectPaths(context, entry, paths);
            } else {
                paths.add(entry);
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        App.stop();
    }
}
