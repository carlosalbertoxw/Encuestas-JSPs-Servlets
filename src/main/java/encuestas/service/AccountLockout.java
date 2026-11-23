package encuestas.service;

import java.time.Duration;
import java.util.Locale;

/**
 * Bloqueo por cuenta: tras varios intentos de inicio de sesión fallidos sobre el mismo
 * correo se rechazan nuevos intentos durante una ventana, incluso con la contraseña
 * correcta. Complementa al límite de peticiones por IP. Los contadores viven en MySQL,
 * compartidos entre réplicas.
 */
public class AccountLockout {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final AttemptCounterStore store;

    public AccountLockout(AttemptCounterStore store) {
        this.store = store;
    }

    public boolean isLocked(String email) {
        return store.count(key(email), WINDOW) >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email) {
        store.increment(key(email), WINDOW);
    }

    public void reset(String email) {
        store.reset(key(email));
    }

    private static String key(String email) {
        return "lockout:" + email.toLowerCase(Locale.ROOT);
    }
}
