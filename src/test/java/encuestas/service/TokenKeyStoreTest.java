package encuestas.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenKeyStoreTest {

    @TempDir
    Path dir;

    @Test
    void createsAndPersistsKeyOnFirstUse() {
        Path file = dir.resolve("token-keys.txt");
        List<byte[]> keys = TokenKeyStore.loadOrCreate(file);
        assertEquals(1, keys.size());
        assertTrue(Files.exists(file));
        // Una segunda carga reutiliza la misma clave (los tokens sobreviven al reinicio).
        List<byte[]> reloaded = TokenKeyStore.loadOrCreate(file);
        assertArrayEquals(keys.get(0), reloaded.get(0));
    }

    @Test
    void rotatesExpiredKeyAndKeepsItForValidation() throws IOException {
        Path file = dir.resolve("token-keys.txt");
        // Clave "vieja" creada hace 91 días.
        byte[] oldKey = "clave-vieja-de-32-bytes-para-hmac".getBytes(StandardCharsets.UTF_8);
        long created = Instant.now().minus(Duration.ofDays(91)).toEpochMilli();
        Files.writeString(file, created + ":" + Base64.getEncoder().encodeToString(oldKey) + "\n");

        List<byte[]> keys = TokenKeyStore.loadOrCreate(file);
        assertEquals(2, keys.size());
        // La nueva firma; la vieja se conserva para validar tokens ya emitidos.
        assertArrayEquals(oldKey, keys.get(1));

        TokenService tokens = new TokenService(keys);
        String issuedWithOldKey = new TokenService(oldKey).createEmailConfirmationToken(7);
        assertNotNull(tokens.validateEmailConfirmationToken(issuedWithOldKey));
        assertEquals(7, tokens.validateEmailConfirmationToken(issuedWithOldKey));
    }
}
