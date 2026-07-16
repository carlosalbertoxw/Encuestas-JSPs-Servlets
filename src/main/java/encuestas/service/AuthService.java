package encuestas.service;

import encuestas.data.UserRepository;
import encuestas.model.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concentra la lógica de autenticación (verificar credenciales, migrar hashes antiguos,
 * emitir la sesión con sus atributos) para mantenerla fuera de los servlets y poder probarla.
 */
public class AuthService {

    public enum LoginResult {
        SUCCESS,
        INVALID_CREDENTIALS,
        EMAIL_NOT_CONFIRMED,
        LOCKED_OUT
    }

    /** Atributos de sesión de la identidad del usuario. */
    public static final String SESSION_ID = "s_id";
    public static final String SESSION_EMAIL = "s_email";
    public static final String SESSION_NAME = "s_name";
    public static final String SESSION_USER = "s_user";
    /** Sello de seguridad; se compara contra la BD para invalidar sesiones. */
    public static final String SESSION_STAMP = "s_stamp";

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    private final UserRepository users;
    private final PasswordService passwords;
    private final AccountLockout lockout;

    public AuthService(UserRepository users, PasswordService passwords, AccountLockout lockout) {
        this.users = users;
        this.passwords = passwords;
        this.lockout = lockout;
    }

    public LoginResult login(HttpServletRequest request, String email, String password) {
        if (lockout.isLocked(email)) {
            LOGGER.log(Level.WARNING, "Inicio de sesión bloqueado por exceso de intentos para {0}", email);
            return LoginResult.LOCKED_OUT;
        }

        UserProfile profile = users.getProfileByEmail(email);
        PasswordService.Outcome outcome = profile == null
                ? PasswordService.Outcome.FAILED
                : passwords.verify(profile.getUser().getPasswordHash(), password);

        if (outcome == PasswordService.Outcome.FAILED) {
            lockout.recordFailure(email);
            LOGGER.log(Level.WARNING, "Intento de inicio de sesión fallido para {0} desde {1}",
                    new Object[]{email, request.getRemoteAddr()});
            return LoginResult.INVALID_CREDENTIALS;
        }

        // Credenciales correctas: se limpia el contador de intentos fallidos.
        lockout.reset(email);

        if (!profile.getUser().isEmailConfirmed()) {
            return LoginResult.EMAIL_NOT_CONFIRMED;
        }

        if (outcome == PasswordService.Outcome.SUCCESS_REHASH_NEEDED) {
            // El hash usa un esquema o parámetros antiguos: se regenera de forma transparente.
            // No rota el sello de seguridad para no cerrar otras sesiones válidas del usuario.
            users.updatePassword(profile.getUser().getId(), passwords.hash(password));
            LOGGER.log(Level.INFO, "Hash de contraseña actualizado para el usuario {0}", profile.getUser().getId());
        }

        signIn(request, profile);
        LOGGER.log(Level.INFO, "Inicio de sesión exitoso del usuario {0}", profile.getUser().getId());
        return LoginResult.SUCCESS;
    }

    /**
     * Emite la sesión autenticada con los datos actuales del perfil. Regenera el id de
     * sesión para impedir la fijación de sesión.
     */
    public void signIn(HttpServletRequest request, UserProfile profile) {
        request.getSession();
        request.changeSessionId();
        setSessionAttributes(request.getSession(), profile);
    }

    /** Reemite los atributos de la sesión tras editar perfil, usuario o correo. */
    public void refreshSession(HttpServletRequest request, int userId) {
        encuestas.model.User user = users.getUser(userId);
        if (user == null) {
            return;
        }
        UserProfile profile = users.getProfileByEmail(user.getEmail());
        if (profile != null) {
            setSessionAttributes(request.getSession(), profile);
        }
    }

    public void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public static boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(SESSION_ID) != null;
    }

    public static int currentUserId(HttpServletRequest request) {
        return (Integer) request.getSession(false).getAttribute(SESSION_ID);
    }

    private static void setSessionAttributes(HttpSession session, UserProfile profile) {
        session.setAttribute(SESSION_ID, profile.getUser().getId());
        session.setAttribute(SESSION_EMAIL, profile.getUser().getEmail());
        session.setAttribute(SESSION_NAME, profile.getName());
        session.setAttribute(SESSION_USER, profile.getUserName());
        session.setAttribute(SESSION_STAMP, profile.getUser().getSecurityStamp());
    }
}
