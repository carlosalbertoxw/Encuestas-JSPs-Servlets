package encuestas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService service =
            new TokenService("secreto-de-pruebas-con-32-caracteres".getBytes(StandardCharsets.UTF_8));

    @Test
    void emailConfirmationTokenRoundTrip() {
        String token = service.createEmailConfirmationToken(42);
        assertEquals(42, service.validateEmailConfirmationToken(token));
    }

    @Test
    void passwordResetTokenRoundTrip() {
        String token = service.createPasswordResetToken(7, "sello-abc");
        TokenService.ResetToken parsed = service.validatePasswordResetToken(token);
        assertNotNull(parsed);
        assertEquals(7, parsed.userId());
        assertEquals("sello-abc", parsed.securityStamp());
    }

    @Test
    void tokensAreNotInterchangeableBetweenPurposes() {
        String confirm = service.createEmailConfirmationToken(42);
        assertNull(service.validatePasswordResetToken(confirm));
        String reset = service.createPasswordResetToken(42, "sello");
        assertNull(service.validateEmailConfirmationToken(reset));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.createEmailConfirmationToken(42);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertNull(service.validateEmailConfirmationToken(tampered));
        assertNull(service.validateEmailConfirmationToken("no-es-un-token"));
        assertNull(service.validateEmailConfirmationToken(""));
        assertNull(service.validateEmailConfirmationToken(null));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        TokenService other =
                new TokenService("otro-secreto-distinto-tambien-largo".getBytes(StandardCharsets.UTF_8));
        String token = other.createEmailConfirmationToken(42);
        assertNull(service.validateEmailConfirmationToken(token));
    }
}
