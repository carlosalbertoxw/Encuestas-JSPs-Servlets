package encuestas.infrastructure;

import encuestas.data.AnswerRepository;
import encuestas.data.Database;
import encuestas.data.DbAttemptCounterStore;
import encuestas.data.EmailOutbox;
import encuestas.data.PollRepository;
import encuestas.data.UserRepository;
import encuestas.service.AccountLockout;
import encuestas.service.AccountService;
import encuestas.service.AttemptCounterStore;
import encuestas.service.AuthService;
import encuestas.service.EmailSender;
import encuestas.service.PasswordService;
import encuestas.service.RateLimiter;
import encuestas.service.SecurityStampCache;
import encuestas.service.SmtpEmailSender;
import encuestas.service.TokenKeyStore;
import encuestas.service.TokenService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Servicios compartidos de la aplicación, construidos una sola vez al arrancar (composición
 * manual, sin framework de inyección). {@link AppInitializer} los pasa por constructor a los
 * servlets y filtros, así que en las pruebas se pueden construir con otra configuración.
 */
public final class App {

    private final AppConfig config;
    private final Database database;
    private final UserRepository users;
    private final PollRepository polls;
    private final AnswerRepository answers;
    private final PasswordService passwords;
    private final SecurityStampCache stampCache;
    private final AuthService auth;
    private final AccountService accounts;
    private final RateLimiter authRateLimiter;
    private final RateLimiter emailChangeRateLimiter;
    private final BackgroundJobs backgroundJobs;

    public App(AppConfig config) {
        this.config = config;
        this.database = new Database(config.getDbHost(), config.getDbPort(), config.getDbName(),
                config.getDbUser(), config.getDbPassword());
        this.users = new UserRepository(database.getDataSource());
        this.polls = new PollRepository(database.getDataSource());
        this.answers = new AnswerRepository(database.getDataSource());
        this.passwords = new PasswordService();
        this.stampCache = new SecurityStampCache();

        // Con APP_TOKEN_SECRET definido se usa ese secreto fijo; si no, un almacén de claves
        // persistidas con rotación (TokenKeyStore).
        byte[] explicitSecret = config.getTokenSecret();
        TokenService tokens = new TokenService(explicitSecret != null
                ? List.of(explicitSecret)
                : TokenKeyStore.loadOrCreate(Path.of(config.getKeysDir()).resolve("token-keys.txt")));

        AttemptCounterStore counters = new DbAttemptCounterStore(database.getDataSource());
        this.auth = new AuthService(users, passwords, new AccountLockout(counters));
        this.authRateLimiter = new RateLimiter(counters, "auth", config.getAuthRateLimit(), Duration.ofMinutes(1));
        this.emailChangeRateLimiter = new RateLimiter(counters, "email-change",
                config.getEmailChangeRateLimit(), Duration.ofHours(1));

        // Los correos siempre salen por SMTP (en Development, a Mailpit por defecto) a través de
        // la bandeja de salida; nunca se escriben en el log, porque llevan enlaces con tokens.
        if (config.getSmtp() == null) {
            throw new IllegalStateException("Falta SMTP_HOST para enviar los correos");
        }
        EmailSender sender = new SmtpEmailSender(config.getSmtp());
        EmailOutbox outbox = new EmailOutbox(database.getDataSource());
        this.accounts = new AccountService(users, passwords, tokens, outbox, stampCache, config.getBaseUrl());
        this.backgroundJobs = new BackgroundJobs(outbox, sender, database.getDataSource());
    }

    /** Arranca las tareas en segundo plano (envío de correos y limpieza). */
    public void startBackgroundJobs() {
        backgroundJobs.start();
    }

    public void close() {
        backgroundJobs.stop();
        database.close();
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

    public SecurityStampCache getStampCache() {
        return stampCache;
    }

    public AuthService getAuth() {
        return auth;
    }

    public AccountService getAccounts() {
        return accounts;
    }

    public RateLimiter getAuthRateLimiter() {
        return authRateLimiter;
    }

    public RateLimiter getEmailChangeRateLimiter() {
        return emailChangeRateLimiter;
    }
}
