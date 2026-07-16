package encuestas.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntFunction;

/**
 * Cachea el sello de seguridad de cada usuario con un TTL corto para no consultar la
 * base de datos en cada petición. Al cambiar la contraseña se invalida la entrada para
 * que la sesión actual (ya reemitida con el sello nuevo) siga siendo válida de inmediato.
 */
public class SecurityStampCache {

    private static final Duration TTL = Duration.ofSeconds(30);

    private record Entry(String stamp, long expiresAt) {
    }

    private final ConcurrentMap<Integer, Entry> cache = new ConcurrentHashMap<>();

    public String get(int userId, IntFunction<String> fetch) {
        long now = System.currentTimeMillis();
        Entry entry = cache.get(userId);
        if (entry == null || now > entry.expiresAt()) {
            entry = new Entry(fetch.apply(userId), now + TTL.toMillis());
            cache.put(userId, entry);
        }
        return entry.stamp();
    }

    public void invalidate(int userId) {
        cache.remove(userId);
    }
}
