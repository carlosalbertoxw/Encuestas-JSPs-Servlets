package encuestas.service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bloqueo por cuenta: tras varios intentos de inicio de sesión fallidos sobre el mismo
 * correo se rechazan nuevos intentos durante una ventana, incluso con la contraseña
 * correcta. Complementa al límite de peticiones por IP. El estado vive en memoria: con
 * múltiples instancias haría falta un almacén compartido.
 */
public class AccountLockout {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Entry(int attempts, long windowEndsAt) {
    }

    private final ConcurrentMap<String, Entry> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        Entry entry = attempts.get(key(email));
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.windowEndsAt()) {
            attempts.remove(key(email));
            return false;
        }
        return entry.attempts() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email) {
        attempts.compute(key(email), (k, entry) -> {
            long now = System.currentTimeMillis();
            if (entry == null || now > entry.windowEndsAt()) {
                return new Entry(1, now + WINDOW.toMillis());
            }
            return new Entry(entry.attempts() + 1, entry.windowEndsAt());
        });
    }

    public void reset(String email) {
        attempts.remove(key(email));
    }

    private static String key(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
