package encuestas.service;

import encuestas.data.EmailOutbox;
import encuestas.data.RepositoryResult;
import encuestas.data.UserRepository;
import encuestas.model.User;
import encuestas.model.UserProfile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Flujos de la cuenta que combinan tokens, correos y el sello de seguridad: registro y
 * confirmación, recuperación de contraseña, cambio de correo y de contraseña, y borrado. Los
 * servlets solo validan la entrada, llaman aquí y eligen el mensaje; lo relativo a la sesión
 * HTTP (emitirla o cerrarla) sigue en {@link AuthService}.
 */
public class AccountService {

    /** Resultado de restablecer la contraseña con un enlace del correo. */
    public enum ResetResult {
        SUCCESS,
        /** Token manipulado, caducado o ya usado (el sello de seguridad cambió). */
        INVALID_TOKEN,
        ERROR
    }

    private static final Logger LOGGER = Logger.getLogger(AccountService.class.getName());

    private final UserRepository users;
    private final PasswordService passwords;
    private final TokenService tokens;
    private final EmailOutbox outbox;
    private final SecurityStampCache stampCache;
    private final String baseUrl;

    public AccountService(UserRepository users, PasswordService passwords, TokenService tokens,
                          EmailOutbox outbox, SecurityStampCache stampCache, String baseUrl) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.outbox = outbox;
        this.stampCache = stampCache;
        this.baseUrl = baseUrl;
    }

    public static String newSecurityStamp() {
        return UUID.randomUUID().toString();
    }

    /** Crea la cuenta y envía el enlace de confirmación. Si el correo ya existe no hace nada. */
    public void register(String email, String password) {
        RepositoryResult result = users.createUser(email, passwords.hash(password), newSecurityStamp());
        UserProfile profile = result == RepositoryResult.SUCCESS ? users.getProfileByEmail(email) : null;
        if (profile != null) {
            sendConfirmation(profile);
            LOGGER.log(Level.INFO, "Cuenta nueva registrada: usuario {0}", profile.getUser().getId());
        }
    }

    /** Reenvía el enlace de confirmación solo si la cuenta existe y sigue sin confirmar. */
    public void resendConfirmation(String email) {
        UserProfile profile = users.getProfileByEmail(email);
        if (profile != null && !profile.getUser().isEmailConfirmed()) {
            sendConfirmation(profile);
            LOGGER.log(Level.INFO, "Enlace de confirmación reenviado al usuario {0}", profile.getUser().getId());
        }
    }

    public boolean confirmEmail(String token) {
        Integer userId = tokens.validateEmailConfirmationToken(token);
        if (userId == null) {
            return false;
        }
        users.confirmEmail(userId);
        LOGGER.log(Level.INFO, "Correo confirmado para el usuario {0}", userId);
        return true;
    }

    public void requestPasswordReset(String email) {
        UserProfile profile = users.getProfileByEmail(email);
        if (profile != null) {
            String token = tokens.createPasswordResetToken(profile.getUser().getId(), profile.getUser().getSecurityStamp());
            outbox.enqueue(email, "Restablece tu contraseña",
                    EmailTemplates.link("Para restablecer tu contraseña visita:", link("/User/ResetPassword", token)));
            LOGGER.log(Level.INFO, "Enlace de restablecimiento generado para el usuario {0}", profile.getUser().getId());
        }
    }

    public boolean isPasswordResetTokenValid(String token) {
        return tokens.validatePasswordResetToken(token) != null;
    }

    public ResetResult resetPassword(String token, String newPassword) {
        // El token incluye el sello de seguridad: si no coincide con el actual, ya se usó o caducó.
        TokenService.ResetToken parsed = tokens.validatePasswordResetToken(token);
        String currentStamp = parsed == null ? null : users.getSecurityStamp(parsed.userId());
        if (parsed == null || currentStamp == null || !currentStamp.equals(parsed.securityStamp())) {
            return ResetResult.INVALID_TOKEN;
        }
        if (users.changePassword(parsed.userId(), passwords.hash(newPassword), newSecurityStamp())
                != RepositoryResult.SUCCESS) {
            return ResetResult.ERROR;
        }
        stampCache.invalidate(parsed.userId());
        LOGGER.log(Level.INFO, "Contraseña restablecida para el usuario {0}", parsed.userId());
        return ResetResult.SUCCESS;
    }

    public boolean verifyPassword(int userId, String password) {
        User user = users.getUser(userId);
        return user != null && passwords.verify(user.getPasswordHash(), password) != PasswordService.Outcome.FAILED;
    }

    /**
     * Deja {@code newEmail} pendiente y envía el enlace a esa dirección, avisando al correo
     * actual. Si otra cuenta ya lo usa, solo se avisa a ese buzón: la respuesta en pantalla es la
     * misma, para no revelar qué correos tienen cuenta.
     */
    public void requestEmailChange(int userId, String currentEmail, String newEmail) {
        if (users.getProfileByEmail(newEmail) != null) {
            outbox.enqueue(newEmail, "Intento de usar tu correo", EmailTemplates.text(
                    "Alguien intentó cambiar el correo de otra cuenta de Encuestas a esta dirección, pero ya "
                            + "tienes una cuenta con ella. No es necesario que hagas nada."));
            return;
        }
        if (users.setPendingEmail(userId, newEmail) == RepositoryResult.SUCCESS) {
            outbox.enqueue(newEmail, "Confirma tu nuevo correo",
                    EmailTemplates.link("Para usar esta dirección en tu cuenta de Encuestas visita:",
                            link("/Account/ConfirmEmailChange", tokens.createEmailChangeToken(userId, newEmail))));
            outbox.enqueue(currentEmail, "Solicitud de cambio de correo", EmailTemplates.text(
                    "Se solicitó cambiar el correo de tu cuenta de Encuestas. Si no fuiste tú, cambia tu "
                            + "contraseña: el cambio no se aplicará mientras nadie abra el enlace enviado al correo nuevo."));
            LOGGER.log(Level.INFO, "Cambio de correo solicitado por el usuario {0}", userId);
        }
    }

    /**
     * Aplica el correo pendiente y rota el sello de seguridad, lo que cierra todas las sesiones.
     * NOT_FOUND si el token no es válido o el cambio ya no está pendiente.
     */
    public RepositoryResult confirmEmailChange(String token) {
        TokenService.EmailChangeToken change = tokens.validateEmailChangeToken(token);
        if (change == null) {
            return RepositoryResult.NOT_FOUND;
        }
        RepositoryResult result = users.confirmEmailChange(change.userId(), change.email(), newSecurityStamp());
        if (result == RepositoryResult.SUCCESS) {
            stampCache.invalidate(change.userId());
            LOGGER.log(Level.INFO, "Correo actualizado para el usuario {0}", change.userId());
        }
        return result;
    }

    /** Cambia la contraseña y rota el sello de seguridad para cerrar las demás sesiones. */
    public boolean changePassword(int userId, String newPassword) {
        if (users.changePassword(userId, passwords.hash(newPassword), newSecurityStamp()) != RepositoryResult.SUCCESS) {
            return false;
        }
        stampCache.invalidate(userId);
        LOGGER.log(Level.INFO, "Contraseña cambiada por el usuario {0}", userId);
        return true;
    }

    public boolean deleteAccount(int userId) {
        if (users.deleteAccount(userId) != RepositoryResult.SUCCESS) {
            return false;
        }
        stampCache.invalidate(userId);
        LOGGER.log(Level.INFO, "Cuenta eliminada por el usuario {0}", userId);
        return true;
    }

    private void sendConfirmation(UserProfile profile) {
        outbox.enqueue(profile.getUser().getEmail(), "Confirma tu cuenta",
                EmailTemplates.link("Para confirmar tu cuenta visita:",
                        link("/User/ConfirmEmail", tokens.createEmailConfirmationToken(profile.getUser().getId()))));
    }

    private String link(String path, String token) {
        return baseUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
