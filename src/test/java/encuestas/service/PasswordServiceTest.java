package encuestas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService();

    @Test
    void hashAndVerifyRoundTrip() {
        String hash = service.hash("secreta123");
        assertTrue(hash.startsWith("PBKDF2$"));
        assertEquals(PasswordService.Outcome.SUCCESS, service.verify(hash, "secreta123"));
    }

    @Test
    void verifyFailsWithWrongPassword() {
        String hash = service.hash("secreta123");
        assertEquals(PasswordService.Outcome.FAILED, service.verify(hash, "otra456"));
    }

    @Test
    void hashesAreSalted() {
        assertNotEquals(service.hash("secreta123"), service.hash("secreta123"));
    }

    @Test
    void legacySha1HashVerifiesAndRequestsRehash() {
        // SHA-1("123456") como lo generaba la versión anterior de la aplicación.
        String legacy = "7c4a8d09ca3762af61e59520943dc26494f8941b";
        assertEquals(PasswordService.Outcome.SUCCESS_REHASH_NEEDED, service.verify(legacy, "123456"));
        assertEquals(PasswordService.Outcome.FAILED, service.verify(legacy, "incorrecta"));
    }

    @Test
    void corruptStoredHashFailsInsteadOfThrowing() {
        assertEquals(PasswordService.Outcome.FAILED, service.verify("PBKDF2$abc$!!$??", "x"));
        assertEquals(PasswordService.Outcome.FAILED, service.verify("basura", "x"));
        assertEquals(PasswordService.Outcome.FAILED, service.verify(null, "x"));
    }

    @Test
    void pbkdf2WithFewerIterationsRequestsRehash() {
        // Hash válido pero con menos iteraciones que la política actual.
        String hash = service.hash("secreta123");
        String[] parts = hash.split("\\$");
        String weaker = weakHash("secreta123", parts[2]);
        assertEquals(PasswordService.Outcome.SUCCESS_REHASH_NEEDED, service.verify(weaker, "secreta123"));
    }

    private static String weakHash(String password, String saltBase64) {
        try {
            byte[] salt = java.util.Base64.getDecoder().decode(saltBase64);
            javax.crypto.spec.PBEKeySpec spec =
                    new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 1000, 256);
            byte[] hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return "PBKDF2$1000$" + saltBase64 + "$" + java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
