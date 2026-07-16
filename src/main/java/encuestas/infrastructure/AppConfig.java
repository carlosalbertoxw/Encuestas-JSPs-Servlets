package encuestas.infrastructure;

import java.nio.charset.StandardCharsets;

/**
 * Configuración de la aplicación leída de variables de entorno (con valores por defecto
 * solo válidos para desarrollo local). Sustituye a las credenciales que antes vivían en
 * el código fuente.
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

    private AppConfig(String dbHost, int dbPort, String dbName, String dbUser, String dbPassword,
                      boolean development, byte[] tokenSecret, String keysDir) {
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.development = development;
        this.tokenSecret = tokenSecret;
        this.keysDir = keysDir;
    }

    public static AppConfig fromEnvironment() {
        String environment = env("APP_ENV", "Development");
        String secret = env("APP_TOKEN_SECRET", "");
        return new AppConfig(
                env("DB_HOST", "localhost"),
                Integer.parseInt(env("DB_PORT", "3306")),
                env("DB_NAME", "encuestas"),
                env("DB_USER", "encuestas"),
                env("DB_PASSWORD", "encuestas"),
                "Development".equalsIgnoreCase(environment),
                secret.isEmpty() ? null : secret.getBytes(StandardCharsets.UTF_8),
                env("APP_KEYS_DIR", System.getProperty("user.home") + "/.encuestas"));
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
}
