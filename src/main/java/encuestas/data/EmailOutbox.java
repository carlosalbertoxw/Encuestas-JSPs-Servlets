package encuestas.data;

import encuestas.service.EmailSender;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Bandeja de salida de correos (tabla {@code a_outbox}). Las peticiones solo encolan, así que
 * nunca esperan al SMTP ni fallan si está caído; {@code OutboxDispatcher} envía en segundo plano
 * con reintentos y espera exponencial. Los mensajes enviados se borran (llevan tokens).
 */
public class EmailOutbox {

    public static final int MAX_ATTEMPTS = 8;
    private static final int BATCH_SIZE = 10;
    private static final Duration BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofHours(1);
    private static final Logger LOGGER = Logger.getLogger(EmailOutbox.class.getName());

    private record Message(long id, String recipient, String subject, String body, int attempts) {
    }

    private final DataSource dataSource;
    // Despierta al despachador en cuanto hay un mensaje nuevo, sin esperar al siguiente sondeo.
    private final Semaphore signal = new Semaphore(0);

    public EmailOutbox(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void enqueue(String recipient, String subject, String htmlBody) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO a_outbox(o_recipient, o_subject, o_body, o_next_attempt_at) "
                             + "VALUES(?, ?, ?, UTC_TIMESTAMP())")) {
            statement.setString(1, recipient);
            statement.setString(2, subject);
            statement.setString(3, htmlBody);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo encolar el correo", e);
        }
        signal.release();
    }

    /** Espera hasta que se encole un mensaje o pase {@code timeout}. */
    public void awaitMessages(Duration timeout) throws InterruptedException {
        if (signal.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            signal.drainPermits();
        }
    }

    /**
     * Envía un lote de mensajes pendientes. Las filas se bloquean con {@code SKIP LOCKED}, así
     * que varias réplicas pueden despachar a la vez sin enviar dos veces el mismo correo.
     *
     * @return número de mensajes procesados (enviados o reprogramados)
     */
    public int dispatchBatch(EmailSender sender) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<Message> messages = lockPending(connection);
                for (Message message : messages) {
                    send(connection, sender, message);
                }
                connection.commit();
                return messages.size();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Espera exponencial: 30 s, 1 min, 2 min… hasta 1 h. */
    public static Duration backoff(int attempts) {
        long seconds = BASE_DELAY.toSeconds() << Math.min(attempts - 1, 20);
        return Duration.ofSeconds(Math.min(seconds, MAX_DELAY.toSeconds()));
    }

    private static List<Message> lockPending(Connection connection) throws SQLException {
        List<Message> messages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT o_key, o_recipient, o_subject, o_body, o_attempts FROM a_outbox "
                        + "WHERE o_failed_at IS NULL AND o_next_attempt_at <= UTC_TIMESTAMP() "
                        + "ORDER BY o_key LIMIT ? FOR UPDATE SKIP LOCKED")) {
            statement.setInt(1, BATCH_SIZE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    messages.add(new Message(rs.getLong(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getInt(5)));
                }
            }
        }
        return messages;
    }

    private static void send(Connection connection, EmailSender sender, Message message) throws SQLException {
        try {
            sender.send(message.recipient(), message.subject(), message.body());
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM a_outbox WHERE o_key=?")) {
                statement.setLong(1, message.id());
                statement.executeUpdate();
            }
        } catch (RuntimeException e) {
            int attempts = message.attempts() + 1;
            boolean failed = attempts >= MAX_ATTEMPTS;
            // El destinatario no se registra: es un dato personal.
            LOGGER.log(Level.WARNING, "Fallo al enviar el correo " + message.id()
                    + " (intento " + attempts + "/" + MAX_ATTEMPTS + ")", e);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE a_outbox SET o_attempts=?, o_last_error=?, "
                            + "o_next_attempt_at=UTC_TIMESTAMP() + INTERVAL ? SECOND, "
                            + "o_failed_at=IF(?, UTC_TIMESTAMP(), NULL) WHERE o_key=?")) {
                statement.setInt(1, attempts);
                statement.setString(2, truncate(String.valueOf(e.getMessage()), 200));
                statement.setLong(3, backoff(attempts).toSeconds());
                statement.setBoolean(4, failed);
                statement.setLong(5, message.id());
                statement.executeUpdate();
            }
        }
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }
}
