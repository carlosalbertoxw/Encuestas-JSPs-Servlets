package encuestas.infrastructure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Formatea los logs de la aplicación como JSON (una línea por evento) con nivel, logger,
 * mensaje, excepción y el identificador de la petición en curso. Se activa fuera de
 * Development con {@link #install()}.
 */
public final class JsonLogFormatter extends Formatter {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final Logger APP_LOGGER = Logger.getLogger("encuestas");
    private static final Object LOCK = new Object();
    private static ConsoleHandler handler;

    /** Envía los logs del paquete {@code encuestas} a la consola en JSON. */
    public static void install() {
        synchronized (LOCK) {
            if (handler == null) {
                handler = new ConsoleHandler();
                handler.setFormatter(new JsonLogFormatter());
                APP_LOGGER.addHandler(handler);
                APP_LOGGER.setUseParentHandlers(false);
            }
        }
    }

    /** Deshace {@link #install()} al detener la aplicación (evita fugas al redesplegar). */
    public static void uninstall() {
        synchronized (LOCK) {
            if (handler != null) {
                APP_LOGGER.removeHandler(handler);
                APP_LOGGER.setUseParentHandlers(true);
                handler = null;
            }
        }
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static void clearRequestId() {
        REQUEST_ID.remove();
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder json = new StringBuilder(256).append('{');
        field(json, "timestamp", Instant.ofEpochMilli(record.getMillis()).toString()).append(',');
        field(json, "level", record.getLevel().getName()).append(',');
        field(json, "logger", record.getLoggerName()).append(',');
        field(json, "message", formatMessage(record));
        String requestId = REQUEST_ID.get();
        if (requestId != null) {
            field(json.append(','), "requestId", requestId);
        }
        if (record.getThrown() != null) {
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            field(json.append(','), "exception", trace.toString());
        }
        return json.append("}\n").toString();
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"');
    }
}
