package encuestas.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Almacén de contadores en memoria para las pruebas unitarias (sin MySQL). */
final class InMemoryAttemptCounterStore implements AttemptCounterStore {

    private record Entry(int count, Instant windowStart) {
    }

    private final Map<String, Entry> counters = new HashMap<>();
    private Instant now = Instant.now();

    /** Adelanta el reloj para simular que la ventana caduca. */
    void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public int count(String key, Duration window) {
        Entry entry = counters.get(key);
        return entry != null && entry.windowStart().isAfter(now.minus(window)) ? entry.count() : 0;
    }

    @Override
    public void increment(String key, Duration window) {
        Entry entry = counters.get(key);
        counters.put(key, entry != null && entry.windowStart().isAfter(now.minus(window))
                ? new Entry(entry.count() + 1, entry.windowStart())
                : new Entry(1, now));
    }

    @Override
    public void reset(String key) {
        counters.remove(key);
    }
}
