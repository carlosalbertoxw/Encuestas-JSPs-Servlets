package encuestas.service;

import java.time.Duration;

/**
 * Límite de peticiones de ventana fija por clave (una IP, un usuario…), con los contadores en
 * MySQL para que el límite se comparta entre réplicas.
 */
public class RateLimiter {

    private final AttemptCounterStore store;
    private final String prefix;
    private final int permitLimit;
    private final Duration window;

    public RateLimiter(AttemptCounterStore store, String prefix, int permitLimit, Duration window) {
        this.store = store;
        this.prefix = prefix;
        this.permitLimit = permitLimit;
        this.window = window;
    }

    /** Registra la petición y devuelve true si está permitida dentro de la ventana actual. */
    public boolean tryAcquire(String key) {
        String fullKey = prefix + ":" + key;
        if (store.count(fullKey, window) >= permitLimit) {
            return false;
        }
        store.increment(fullKey, window);
        return true;
    }
}
