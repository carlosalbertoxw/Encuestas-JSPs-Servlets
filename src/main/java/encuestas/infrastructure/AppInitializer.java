package encuestas.infrastructure;

import encuestas.web.AccountServlet;
import encuestas.web.AnswerServlet;
import encuestas.web.HealthServlet;
import encuestas.web.PollServlet;
import encuestas.web.UserServlet;
import encuestas.web.filter.AuthFilter;
import encuestas.web.filter.CsrfFilter;
import encuestas.web.filter.EncodingFilter;
import encuestas.web.filter.ProxyHeadersFilter;
import encuestas.web.filter.RequestIdFilter;
import encuestas.web.filter.SecurityHeadersFilter;
import encuestas.web.filter.SecurityStampFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de arranque de la aplicación: construye los servicios, aplica las migraciones
 * pendientes antes de aceptar tráfico, en Development inserta los datos de demostración y
 * registra los filtros y servlets pasándoles los servicios por constructor.
 */
@WebListener
public class AppInitializer implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AppInitializer.class.getName());
    /** Rutas de la aplicación (las que llevan formularios y sesión), sin los archivos estáticos. */
    private static final String[] APP_PATHS = {"", "/User/*", "/Account/*", "/Poll/*", "/Answer/*"};

    private App app;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        AppConfig config = AppConfig.fromEnvironment();
        if (!config.isDevelopment()) {
            JsonLogFormatter.install();
        }
        app = new App(config);
        MigrationRunner.run(app.getDatabase().getDataSource());
        if (config.isDevelopment()) {
            DevDataSeeder.seed(app);
        }
        app.startBackgroundJobs();

        ServletContext context = event.getServletContext();
        registerFilters(context);
        registerServlets(context);
        // Fuera de Development la cookie de sesión solo viaja por HTTPS (los navegadores
        // tratan a localhost como origen seguro, así que el uso local sigue funcionando).
        context.getSessionCookieConfig().setSecure(!config.isDevelopment());
        // Versión de los assets derivada de su contenido, para el cache busting (?v=...).
        context.setAttribute("assetsVersion", computeAssetsVersion(context));
        LOGGER.log(Level.INFO, "Aplicación iniciada (entorno {0})",
                config.isDevelopment() ? "Development" : "Production");
    }

    /** El orden de registro es el orden de ejecución. */
    private void registerFilters(ServletContext context) {
        addFilter(context, "requestId", new RequestIdFilter(), "/*");
        addFilter(context, "encoding", new EncodingFilter(), "/*");
        // Antes que el resto: fija la IP y el esquema reales que usan el límite de peticiones y HSTS.
        addFilter(context, "proxyHeaders", new ProxyHeadersFilter(app.getConfig().getTrustedProxies()), "/*");
        addFilter(context, "securityHeaders", new SecurityHeadersFilter(app.getConfig().isDevelopment()), "/*");
        addFilter(context, "securityStamp", new SecurityStampFilter(app.getUsers(), app.getStampCache()), "/*");
        addFilter(context, "csrf", new CsrfFilter(), APP_PATHS);
        addFilter(context, "auth", new AuthFilter(), "/Account/*", "/Poll/*", "/Answer/*");
    }

    private void registerServlets(ServletContext context) {
        // "" es la raíz exacta del contexto: la página de inicio.
        context.addServlet("user", new UserServlet(app)).addMapping("", "/User/*");
        context.addServlet("account", new AccountServlet(app)).addMapping("/Account/*");
        context.addServlet("poll", new PollServlet(app)).addMapping("/Poll/*");
        context.addServlet("answer", new AnswerServlet(app)).addMapping("/Answer/*");
        context.addServlet("health", new HealthServlet(app.getDatabase())).addMapping("/health/live", "/health/ready");
    }

    private static void addFilter(ServletContext context, String name, Filter filter, String... patterns) {
        context.addFilter(name, filter)
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, patterns);
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
        if (app != null) {
            app.close();
        }
        JsonLogFormatter.uninstall();
    }
}
