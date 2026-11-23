package encuestas.infrastructure;

import encuestas.data.EmailOutbox;
import encuestas.service.EmailSender;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Tareas en segundo plano de la aplicación: el despachador de la bandeja de salida de correos
 * (despierta al encolarse un mensaje o, como mínimo, cada 15 s para recoger los reintentos) y
 * la limpieza horaria de contadores caducados y de correos que agotaron sus reintentos.
 */
public final class BackgroundJobs {

    private static final Logger LOGGER = Logger.getLogger(BackgroundJobs.class.getName());
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(15);
    private static final Duration ERROR_DELAY = Duration.ofSeconds(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

    private final EmailOutbox outbox;
    private final EmailSender sender;
    private final DataSource dataSource;
    private Thread dispatcher;
    private Thread cleanup;

    public BackgroundJobs(EmailOutbox outbox, EmailSender sender, DataSource dataSource) {
        this.outbox = outbox;
        this.sender = sender;
        this.dataSource = dataSource;
    }

    public void start() {
        dispatcher = Thread.ofVirtual().name("encuestas-outbox").start(this::dispatchLoop);
        cleanup = Thread.ofVirtual().name("encuestas-cleanup").start(this::cleanupLoop);
    }

    public void stop() {
        for (Thread thread : new Thread[]{dispatcher, cleanup}) {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(Duration.ofSeconds(5));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void dispatchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                while (outbox.dispatchBatch(sender) > 0) {
                    // Sigue mientras haya mensajes listos para enviar.
                }
                outbox.awaitMessages(POLL_INTERVAL);
            } catch (InterruptedException e) {
                return;
            } catch (SQLException | RuntimeException e) {
                // P. ej. la base de datos no responde: se reintenta más tarde sin detener el hilo.
                LOGGER.log(Level.SEVERE, "Error al procesar la bandeja de salida de correos", e);
                if (!sleep(ERROR_DELAY)) {
                    return;
                }
            }
        }
    }

    private void cleanupLoop() {
        do {
            // La ventana más larga de los contadores es de una hora; un día deja margen de sobra.
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "DELETE FROM a_attempt_counters WHERE ac_window_start < UTC_TIMESTAMP() - INTERVAL 1 DAY");
                statement.executeUpdate(
                        "DELETE FROM a_outbox WHERE o_failed_at < UTC_TIMESTAMP() - INTERVAL 7 DAY");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "No se pudo limpiar la base de datos; se reintentará más tarde", e);
            }
        } while (sleep(CLEANUP_INTERVAL));
    }

    /** Duerme el intervalo; devuelve false si el hilo se interrumpió (la aplicación se detiene). */
    private static boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
