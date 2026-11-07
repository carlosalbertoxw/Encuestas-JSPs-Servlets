package encuestas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogRedactionTest {

    @Test
    void masksTheLocalPartOfTheEmail() {
        assertEquals("d***@encuestas.dev", LogRedaction.email("demo@encuestas.dev"));
    }

    @Test
    void handlesValuesThatAreNotEmails() {
        assertEquals("***", LogRedaction.email("sin-arroba"));
        assertEquals("", LogRedaction.email(""));
        assertEquals("", LogRedaction.email(null));
    }
}
