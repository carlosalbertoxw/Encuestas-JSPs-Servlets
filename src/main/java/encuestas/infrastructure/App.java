package encuestas.infrastructure;

import encuestas.data.AnswerRepository;
import encuestas.data.Database;
import encuestas.data.PollRepository;
import encuestas.data.UserRepository;
import encuestas.service.AccountLockout;
import encuestas.service.AuthService;
import encuestas.service.EmailSender;
import encuestas.service.LoggingEmailSender;
import encuestas.service.PasswordService;
import encuestas.service.RateLimiter;
import encuestas.service.SecurityStampCache;
import encuestas.service.TokenKeyStore;
import encuestas.service.TokenService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Contenedor de los servicios compartidos de la aplicación (composición manual, sin
 * framework de inyección). Lo construye {@link AppInitializer} al arrancar y lo consumen
 * los servlets y filtros.
 */
public final class App {

    private static volatile App instance;

    private final AppConfig config;
    private final Database database;
    private final UserRepository users;
    private final PollRepository polls;
    private final AnswerRepository answers;
    private final PasswordService passwords;
    private final TokenService tokens;
    private final AccountLockout lockout;
    private final RateLimiter authRateLimiter;
    private final SecurityStampCache stampCache;
    private final AuthService auth;
    private final EmailSender email;

    private App(AppConfig config) {
        this.config = config;
        this.database = new Database(config.getDbHost(), config.getDbPort(), config.getDbName(),
                config.getDbUser(), config.getDbPassword());
        this.users = new UserRepository(database.getDataSource());
        this.polls = new PollRepository(database.getDataSource());
        this.answers = new AnswerRepository(database.getDataSource());
        this.passwords = new PasswordService();
        // Con APP_TOKEN_SECRET definido se usa ese secreto fijo; si no, un almacén de
        // claves persistidas con rotación (TokenKeyStore).
        byte[] explicitSecret = config.getTokenSecret();
        this.tokens = new TokenService(explicitSecret != null
                ? List.of(explicitSecret)
                : TokenKeyStore.loadOrCreate(Path.of(config.getKeysDir()).resolve("token-keys.txt")));
        this.lockout = new AccountLockout();
        this.authRateLimiter = new RateLimiter(10, Duration.ofMinutes(1));
        this.stampCache = new SecurityStampCache();
        this.auth = new AuthService(users, passwords, lockout);
        this.email = new LoggingEmailSender();
    }

    static App start(AppConfig config) {
        App app = new App(config);
        instance = app;
        return app;
    }

    static void stop() {
        App app = instance;
        instance = null;
        if (app != null) {
            app.database.close();
        }
    }

    public static App get() {
        App app = instance;
        if (app == null) {
            throw new IllegalStateException("La aplicación no está inicializada");
        }
        return app;
    }

    public AppConfig getConfig() {
        return config;
    }

    public Database getDatabase() {
        return database;
    }

    public UserRepository getUsers() {
        return users;
    }

    public PollRepository getPolls() {
        return polls;
    }

    public AnswerRepository getAnswers() {
        return answers;
    }

    public PasswordService getPasswords() {
        return passwords;
    }

    public TokenService getTokens() {
        return tokens;
    }

    public RateLimiter getAuthRateLimiter() {
        return authRateLimiter;
    }

    public SecurityStampCache getStampCache() {
        return stampCache;
    }

    public AuthService getAuth() {
        return auth;
    }

    public EmailSender getEmail() {
        return email;
    }
}
