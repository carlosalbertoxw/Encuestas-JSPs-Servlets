package encuestas.infrastructure;

import encuestas.service.SmtpEmailSender.SmtpSettings;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Configuración de la aplicación leída de variables de entorno (o propiedades del sistema con
 * el mismo nombre), con valores por defecto solo válidos para desarrollo local.
 */
public final class AppConfig {

    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final boolean development;
    private final byte[] tokenSecret;
    private final String keysDir;
    private final String baseUrl;
    private final SmtpSettings smtp;
    private final TrustedProxies trustedProxies;
    private final int authRateLimit;
    private final int emailChangeRateLimit;

    private AppConfig(Builder builder) {
        this.dbHost = builder.dbHost;
        this.dbPort = builder.dbPort;
        this.dbName = builder.dbName;
        this.dbUser = builder.dbUser;
        this.dbPassword = builder.dbPassword;
        this.development = builder.development;
        this.tokenSecret = builder.tokenSecret;
        this.keysDir = builder.keysDir;
        this.baseUrl = builder.baseUrl;
        this.smtp = builder.smtp;
        this.trustedProxies = builder.trustedProxies;
        this.authRateLimit = builder.authRateLimit;
        this.emailChangeRateLimit = builder.emailChangeRateLimit;
    }

    public static AppConfig fromEnvironment() {
        Builder builder = new Builder();
        builder.development = "Development".equalsIgnoreCase(env("APP_ENV", "Development"));
        builder.dbHost = env("DB_HOST", "localhost");
        builder.dbPort = parsePort("DB_PORT", "3306");
        builder.dbName = env("DB_NAME", "encuestas");
        builder.dbUser = env("DB_USER", "encuestas");
        builder.dbPassword = env("DB_PASSWORD", "encuestas");
        String secret = env("APP_TOKEN_SECRET", "");
        builder.tokenSecret = secret.isEmpty() ? null : secret.getBytes(StandardCharsets.UTF_8);
        builder.keysDir = env("APP_KEYS_DIR", System.getProperty("user.home") + "/.encuestas");
        builder.baseUrl = parseBaseUrl(env("APP_BASE_URL", builder.development ? "http://localhost:8080" : ""));
        // En Development, por defecto los correos van a Mailpit (localhost:1025, sin TLS).
        String smtpHost = env("SMTP_HOST", builder.development ? "localhost" : "");
        builder.smtp = smtpHost.isEmpty() ? null : new SmtpSettings(
                smtpHost,
                parsePort("SMTP_PORT", builder.development ? "1025" : "587"),
                Boolean.parseBoolean(env("SMTP_STARTTLS", builder.development ? "false" : "true")),
                env("SMTP_USER", ""),
                env("SMTP_PASSWORD", ""),
                env("SMTP_FROM", "Encuestas <no-reply@encuestas.local>"));
        // Proxies inversos cuyas cabeceras X-Forwarded-* se aceptan; vacío = no se confía en ninguno.
        builder.trustedProxies = TrustedProxies.parse(env("TRUSTED_PROXIES", ""));
        // Peticiones permitidas por ventana (formularios de acceso por IP y minuto; cambios de
        // correo por usuario y hora). Las pruebas los amplían.
        builder.authRateLimit = parsePositive("RATE_LIMIT_AUTH", "10");
        builder.emailChangeRateLimit = parsePositive("RATE_LIMIT_EMAIL_CHANGE", "3");
        return new AppConfig(builder);
    }

    /**
     * Valida la URL pública con la que se construyen los enlaces de los correos. Es obligatoria
     * fuera de Development: tomarla de la cabecera Host permitiría que un atacante hiciera
     * apuntar el enlace de restablecimiento de otra persona a su dominio.
     */
    static String parseBaseUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new URISyntaxException(value, "Se esperaba una URL http(s) absoluta");
            }
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Falta APP_BASE_URL, la URL pública de la aplicación "
                    + "(p. ej. https://encuestas.example.com)", e);
        }
    }

    /** Lee un puerto TCP; un valor no numérico o fuera de rango detiene el arranque con un mensaje claro. */
    static int parsePort(String name, String defaultValue) {
        String value = env(name, defaultValue);
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException(value);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " debe ser un puerto válido (1-65535): " + value, e);
        }
    }

    static int parsePositive(String name, String defaultValue) {
        String value = env(name, defaultValue);
        try {
            int number = Integer.parseInt(value);
            if (number < 1) {
                throw new NumberFormatException(value);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " debe ser un entero positivo: " + value, e);
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public boolean isDevelopment() {
        return development;
    }

    /** Secreto explícito para firmar tokens, o null para usar el almacén de claves rotadas. */
    public byte[] getTokenSecret() {
        return tokenSecret == null ? null : tokenSecret.clone();
    }

    /** Directorio donde se persisten las claves de tokens generadas por la aplicación. */
    public String getKeysDir() {
        return keysDir;
    }

    /** URL pública sin barra final (p. ej. {@code https://encuestas.example.com}). */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Servidor SMTP, o null si no está configurado. */
    public SmtpSettings getSmtp() {
        return smtp;
    }

    public TrustedProxies getTrustedProxies() {
        return trustedProxies;
    }

    public int getAuthRateLimit() {
        return authRateLimit;
    }

    public int getEmailChangeRateLimit() {
        return emailChangeRateLimit;
    }

    private static final class Builder {
        private String dbHost;
        private int dbPort;
        private String dbName;
        private String dbUser;
        private String dbPassword;
        private boolean development;
        private byte[] tokenSecret;
        private String keysDir;
        private String baseUrl;
        private SmtpSettings smtp;
        private TrustedProxies trustedProxies;
        private int authRateLimit;
        private int emailChangeRateLimit;
    }
}
