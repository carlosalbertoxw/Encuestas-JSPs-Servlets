package encuestas.service;

/** Envío de correos transaccionales (confirmación de cuenta, restablecimiento). */
public interface EmailSender {

    void send(String recipient, String subject, String htmlBody);
}
