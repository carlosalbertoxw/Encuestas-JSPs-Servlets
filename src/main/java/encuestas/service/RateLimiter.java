package encuestas.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite de peticiones por clave (IP) con ventana fija, para frenar fuerza bruta en el
 * inicio de sesión y el registro masivo de cuentas.
 */
public class RateLimiter {

    private record Window(long endsAt, AtomicInteger count) {
    }

    private final int permitLimit;
    private final long windowMillis;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int permitLimit, Duration window) {
        this.permitLimit = permitLimit;
        this.windowMillis = window.toMillis();
    }

    /** Devuelve true si la petición está permitida dentro de la ventana actual. */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, current) ->
                current == null || now > current.endsAt()
                        ? new Window(now + windowMillis, new AtomicInteger())
                        : current);
        boolean allowed = window.count().incrementAndGet() <= permitLimit;
        // Limpieza oportunista para que el mapa no crezca sin límite con IPs efímeras.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now > entry.getValue().endsAt());
        }
        return allowed;
    }
}
