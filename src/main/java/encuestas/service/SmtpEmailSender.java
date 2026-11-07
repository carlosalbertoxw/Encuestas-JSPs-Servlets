package encuestas.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Envía los correos por SMTP con Jakarta Mail (en local, a Mailpit). */
public class SmtpEmailSender implements EmailSender {

    private final Session session;
    private final String from;

    public SmtpEmailSender(SmtpSettings settings) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", settings.host());
        properties.put("mail.smtp.port", String.valueOf(settings.port()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(settings.startTls()));
        properties.put("mail.smtp.starttls.required", String.valueOf(settings.startTls()));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        boolean auth = settings.user() != null && !settings.user().isEmpty();
        properties.put("mail.smtp.auth", String.valueOf(auth));
        this.session = auth
                ? Session.getInstance(properties, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(settings.user(), settings.password());
                    }
                })
                : Session.getInstance(properties);
        this.from = settings.from();
    }

    @Override
    public void send(String recipient, String subject, String htmlBody) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setText(htmlBody, StandardCharsets.UTF_8.name(), "html");
            Transport.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("No se pudo enviar el correo", e);
        }
    }

    /** Configuración del servidor SMTP. */
    public record SmtpSettings(String host, int port, boolean startTls, String user, String password, String from) {
    }
}
