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
    }

    public static AppConfig fromEnvironment() {
        Builder builder = new Builder();
        builder.development = "Development".equalsIgnoreCase(env("APP_ENV", "Development"));
        builder.dbHost = env("DB_HOST", "localhost");
        builder.dbPort = Integer.parseInt(env("DB_PORT", "3306"));
        builder.dbName = env("DB_NAME", "encuestas");
        builder.dbUser = env("DB_USER", "encuestas");
        builder.dbPassword = env("DB_PASSWORD", "encuestas");
        String secret = env("APP_TOKEN_SECRET", "");
        builder.tokenSecret = secret.isEmpty() ? null : secret.getBytes(StandardCharsets.UTF_8);
        builder.keysDir = env("APP_KEYS_DIR", System.getProperty("user.home") + "/.encuestas");
        builder.baseUrl = parseBaseUrl(env("APP_BASE_URL", builder.development ? "http://localhost:8080" : ""));
        String smtpHost = env("SMTP_HOST", "");
        builder.smtp = smtpHost.isEmpty() ? null : new SmtpSettings(
                smtpHost,
                Integer.parseInt(env("SMTP_PORT", "587")),
                Boolean.parseBoolean(env("SMTP_STARTTLS", "true")),
                env("SMTP_USER", ""),
                env("SMTP_PASSWORD", ""),
                env("SMTP_FROM", "Encuestas <no-reply@encuestas.local>"));
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
    }
}
