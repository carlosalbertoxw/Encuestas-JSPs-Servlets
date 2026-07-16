package encuestas.service;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación por defecto que registra el correo en el log en lugar de enviarlo.
 * Sirve para desarrollo y pruebas; en producción debe sustituirse por un proveedor
 * real (SMTP, SendGrid…).
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger LOGGER = Logger.getLogger(LoggingEmailSender.class.getName());

    @Override
    public void send(String recipient, String subject, String htmlBody) {
        LOGGER.log(Level.INFO, "[Correo simulado] Para: {0} | Asunto: {1}\n{2}",
                new Object[]{recipient, subject, htmlBody});
    }
}
