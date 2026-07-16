package encuestas.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestión de las claves con las que se firman los tokens de correo cuando no hay un
 * secreto configurado: las persiste en un archivo (una por línea, {@code epochMillis:base64})
 * para que los enlaces sobrevivan a los reinicios, rota la clave activa cada 90 días y
 * conserva las últimas para seguir validando los tokens emitidos con ellas.
 */
public final class TokenKeyStore {

    private static final Logger LOGGER = Logger.getLogger(TokenKeyStore.class.getName());

    private static final Duration ROTATION_PERIOD = Duration.ofDays(90);
    private static final int MAX_KEYS = 3;
    private static final int KEY_BYTES = 32;
    private static final char SEPARATOR = ':';
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(long createdAt, byte[] key) {
    }

    /**
     * Devuelve las claves vigentes, la activa (más reciente) primero. Si el archivo no
     * existe o la clave activa ya cumplió el periodo de rotación, genera una nueva y la
     * persiste. Si el archivo no se puede escribir, devuelve una clave efímera.
     */
    public static List<byte[]> loadOrCreate(Path file) {
        List<Entry> entries = read(file);
        long now = Instant.now().toEpochMilli();
        boolean rotationDue = entries.isEmpty()
                || now - entries.get(0).createdAt() > ROTATION_PERIOD.toMillis();
        if (rotationDue) {
            byte[] key = new byte[KEY_BYTES];
            RANDOM.nextBytes(key);
            entries.add(0, new Entry(now, key));
            if (entries.size() > MAX_KEYS) {
                entries = new ArrayList<>(entries.subList(0, MAX_KEYS));
            }
            write(file, entries);
        }
        return entries.stream().map(Entry::key).toList();
    }

    private static List<Entry> read(Path file) {
        List<Entry> entries = new ArrayList<>();
        if (!Files.exists(file)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                int separator = line.indexOf(SEPARATOR);
                if (line.isEmpty() || separator < 1) {
                    continue;
                }
                try {
                    entries.add(new Entry(Long.parseLong(line.substring(0, separator)),
                            Base64.getDecoder().decode(line.substring(separator + 1))));
                } catch (IllegalArgumentException e) {
                    LOGGER.log(Level.WARNING, "Línea de clave ilegible en {0}; se ignora", file);
                }
            }
            // La clave activa (más reciente) primero.
            entries.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo leer el archivo de claves " + file, e);
        }
        return entries;
    }

    private static void write(Path file, List<Entry> entries) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            StringBuilder content = new StringBuilder();
            for (Entry entry : entries) {
                content.append(entry.createdAt()).append(SEPARATOR)
                        .append(Base64.getEncoder().encodeToString(entry.key())).append('\n');
            }
            Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
            LOGGER.log(Level.INFO, "Clave de tokens rotada y persistida en {0}", file);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo persistir el archivo de claves " + file
                    + "; los tokens caducarán al reiniciar", e);
        }
    }

    private TokenKeyStore() {
    }
}
