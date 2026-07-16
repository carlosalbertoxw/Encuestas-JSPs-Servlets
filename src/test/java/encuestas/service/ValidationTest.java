package encuestas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidationTest {

    @Test
    void emailValidation() {
        assertTrue(Validation.isEmail("demo@encuestas.dev"));
        assertFalse(Validation.isEmail("sin-arroba"));
        assertFalse(Validation.isEmail("a@b"));
        assertFalse(Validation.isEmail(""));
        assertFalse(Validation.isEmail(null));
        assertFalse(Validation.isEmail("a".repeat(50) + "@x.com"));
    }

    @Test
    void newPasswordPolicy() {
        assertTrue(Validation.isNewPassword("123456"));
        assertFalse(Validation.isNewPassword("12345"));
        assertFalse(Validation.isNewPassword("a".repeat(51)));
    }

    @Test
    void legacyShortPasswordsCanStillSignIn() {
        assertTrue(Validation.isPassword("abc"));
        assertFalse(Validation.isPassword(""));
    }

    @Test
    void userNamePattern() {
        assertTrue(Validation.isUserName("usuario-1"));
        assertFalse(Validation.isUserName("usuario con espacios"));
        assertFalse(Validation.isUserName("ñandu"));
        assertFalse(Validation.isUserName("a".repeat(26)));
    }

    @Test
    void pollFields() {
        assertTrue(Validation.isPollTitle("Título"));
        assertFalse(Validation.isPollTitle("a".repeat(251)));
        assertTrue(Validation.isPollDescription("Descripción"));
        assertFalse(Validation.isPollDescription(""));
        assertTrue(Validation.isPollPosition(1));
        assertFalse(Validation.isPollPosition(0));
        assertFalse(Validation.isPollPosition(1_000_000));
        assertFalse(Validation.isPollPosition(null));
    }

    @Test
    void answerFields() {
        assertTrue(Validation.isStars(1));
        assertTrue(Validation.isStars(5));
        assertFalse(Validation.isStars(0));
        assertFalse(Validation.isStars(6));
        assertTrue(Validation.isComment(null));
        assertTrue(Validation.isComment(""));
        assertFalse(Validation.isComment("a".repeat(1001)));
    }

    @Test
    void parseIntTolerantOfBadInput() {
        assertEquals(5, Validation.parseInt("5"));
        assertEquals(5, Validation.parseInt(" 5 "));
        assertNull(Validation.parseInt("abc"));
        assertNull(Validation.parseInt(null));
    }
}
