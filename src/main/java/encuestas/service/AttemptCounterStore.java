package encuestas.service;

import java.time.Duration;

/**
 * Contador de ventana fija compartido entre peticiones (y entre réplicas). Lo usan el límite
 * de peticiones y el bloqueo por cuenta; la interfaz permite un almacén en memoria en las pruebas.
 */
public interface AttemptCounterStore {

    /** Intentos registrados para la clave dentro de su ventana vigente. */
    int count(String key, Duration window);

    /** Registra un intento; si la ventana anterior caducó, empieza una nueva. */
    void increment(String key, Duration window);

    void reset(String key);
}
