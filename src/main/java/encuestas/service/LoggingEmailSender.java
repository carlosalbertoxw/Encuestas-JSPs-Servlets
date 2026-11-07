package encuestas.service;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Escribe el correo en el log en lugar de enviarlo. Solo se usa en Development cuando no hay
 * SMTP configurado; el cuerpo incluye enlaces con tokens.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger LOGGER = Logger.getLogger(LoggingEmailSender.class.getName());

    @Override
    public void send(String recipient, String subject, String htmlBody) {
        LOGGER.log(Level.INFO, "[Correo simulado] Para: {0} | Asunto: {1}\n{2}",
                new Object[]{recipient, subject, htmlBody});
    }
}
