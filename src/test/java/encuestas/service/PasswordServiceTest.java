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
    void corruptStoredHashFailsInsteadOfThrowing() {
        assertEquals(PasswordService.Outcome.FAILED, service.verify("PBKDF2$abc$!!$??", "x"));
        assertEquals(PasswordService.Outcome.FAILED, service.verify("basura", "x"));
        assertEquals(PasswordService.Outcome.FAILED, service.verify(null, "x"));
    }

    @Test
    void pbkdf2WithFewerIterationsRequestsRehash() {
        // Hash válido pero con menos iteraciones que la política actual.
        String weaker = service.hash("secreta123", 210_000);
        assertEquals(PasswordService.Outcome.SUCCESS_REHASH_NEEDED, service.verify(weaker, "secreta123"));
        assertTrue(service.hash("secreta123").startsWith("PBKDF2$" + PasswordService.ITERATIONS + "$"));
    }

    @Test
    void unsaltedSha1HashesAreRejected() {
        // SHA-1("123456"): formato sin sal que la aplicación ya no acepta.
        assertEquals(PasswordService.Outcome.FAILED, service.verify("7c4a8d09ca3762af61e59520943dc26494f8941b", "123456"));
    }

    @Test
    void dummyVerificationDoesNotThrow() {
        service.verifyAgainstDummy("cualquiera");
        service.verifyAgainstDummy("");
    }
}
