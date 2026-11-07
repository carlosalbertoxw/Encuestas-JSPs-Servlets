package encuestas.web;

import encuestas.data.PagedResult;
import encuestas.data.RepositoryResult;
import encuestas.infrastructure.App;
import encuestas.model.UserProfile;
import encuestas.service.AuthService;
import encuestas.service.EmailTemplates;
import encuestas.service.Messages;
import encuestas.service.TokenService;
import encuestas.service.Validation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Página de inicio, perfiles públicos y flujos de acceso: inicio de sesión, registro con
 * confirmación de correo, recuperación de contraseña y cierre de sesión. La gestión de la
 * cuenta autenticada vive en {@link AccountServlet}.
 */
public class UserServlet extends BaseServlet {

    private static final Logger LOGGER = Logger.getLogger(UserServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String page = param(request, "page");
        String profile = param(request, "profile");

        // Páginas accesibles con o sin sesión.
        switch (page) {
            case "confirm-email" -> {
                confirmEmail(request, response);
                return;
            }
            case "forgot-password" -> {
                forward(request, response, "public/forgotPassword.jsp", "Recuperar contraseña");
                return;
            }
            case "reset-password" -> {
                resetPassword(request, response);
                return;
            }
            default -> {
            }
        }

        if (!AuthService.isAuthenticated(request)) {
            if (page.isEmpty()) {
                forward(request, response, "public/home.jsp", "Inicio");
            } else {
                notFound(request, response);
            }
            return;
        }

        if (!profile.isEmpty()) {
            profile(request, response, profile);
        } else if (page.isEmpty()) {
            redirect(request, response, "/Poll?page=dashboard");
        } else {
            notFound(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String form = param(request, "form");
        if (AuthService.isAuthenticated(request)) {
            if ("close-session".equals(form)) {
                App.get().getAuth().signOut(request);
            }
            redirect(request, response, "/");
            return;
        }
        switch (form) {
            case "sign-in" -> signIn(request, response);
            case "sign-up" -> signUp(request, response);
            case "forgot-password" -> forgotPassword(request, response);
            case "reset-password" -> resetPasswordForm(request, response);
            default -> redirect(request, response, "/");
        }
    }

    /** Perfil público por nombre de usuario, con una página de sus encuestas. */
    private void profile(HttpServletRequest request, HttpServletResponse response, String profile)
            throws ServletException, IOException {
        UserProfile userProfile = profile.length() > 25 ? null : App.get().getUsers().getProfileByUserName(profile);
        if (userProfile == null) {
            notFound(request, response);
            return;
        }
        request.setAttribute("result", App.get().getPolls().getPolls(userProfile.getUser().getId(),
                pageNumber(request), PagedResult.DEFAULT_PAGE_SIZE));
        request.setAttribute("profileUserName", userProfile.getUserName());
        forward(request, response, "session/profile.jsp",
                userProfile.getName() + " (" + userProfile.getUserName() + ")");
    }

    private void signIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        String password = param(request, "password");
        if (!Validation.isEmail(email) || !Validation.isPassword(password)) {
            rejectSignIn(request, response, email, Messages.INVALID_CREDENTIALS);
            return;
        }
        switch (App.get().getAuth().login(request, email, password)) {
            case SUCCESS -> redirect(request, response, "/Poll?page=dashboard");
            case EMAIL_NOT_CONFIRMED -> rejectSignIn(request, response, email, Messages.EMAIL_NOT_CONFIRMED);
            case LOCKED_OUT -> rejectSignIn(request, response, email, Messages.ACCOUNT_LOCKED);
            default -> rejectSignIn(request, response, email, Messages.INVALID_CREDENTIALS);
        }
    }

    private void rejectSignIn(HttpServletRequest request, HttpServletResponse response,
                              String email, String message) throws IOException {
        setFlash(request, message);
        setFlashOld(request, Map.of("form", "sign-in", "email", email));
        redirect(request, response, "/");
    }

    private void signUp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        String password = param(request, "password");
        String rePassword = param(request, "re_password");
        if (!Validation.isEmail(email) || !Validation.isNewPassword(password) || !password.equals(rePassword)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("form", "sign-up", "email", email));
            redirect(request, response, "/");
            return;
        }
        App app = App.get();
        RepositoryResult result = app.getUsers().createUser(email,
                app.getPasswords().hash(password), UUID.randomUUID().toString());
        UserProfile profile = result == RepositoryResult.SUCCESS ? app.getUsers().getProfileByEmail(email) : null;
        if (profile != null) {
            int userId = profile.getUser().getId();
            String link = absoluteUrl("/User?page=confirm-email&token="
                    + encode(app.getTokens().createEmailConfirmationToken(userId)));
            app.getEmail().send(email, "Confirma tu cuenta",
                    EmailTemplates.link("Para confirmar tu cuenta visita:", link));
            LOGGER.log(Level.INFO, "Cuenta nueva registrada: usuario {0}", userId);
        }
        // Se responde lo mismo exista o no el correo, para no revelar cuentas.
        setFlash(request, Messages.REGISTER_ACKNOWLEDGED);
        redirect(request, response, "/");
    }

    private void confirmEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = App.get().getTokens().validateEmailConfirmationToken(param(request, "token"));
        if (userId == null) {
            setFlash(request, Messages.EMAIL_CONFIRMATION_INVALID);
        } else {
            App.get().getUsers().confirmEmail(userId);
            LOGGER.log(Level.INFO, "Correo confirmado para el usuario {0}", userId);
            setFlash(request, Messages.EMAIL_CONFIRMED);
        }
        redirect(request, response, "/");
    }

    private void forgotPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        if (!Validation.isEmail(email)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("email", email));
            redirect(request, response, "/User?page=forgot-password");
            return;
        }
        App app = App.get();
        UserProfile profile = app.getUsers().getProfileByEmail(email);
        if (profile != null) {
            String token = app.getTokens().createPasswordResetToken(
                    profile.getUser().getId(), profile.getUser().getSecurityStamp());
            String link = absoluteUrl("/User?page=reset-password&token=" + encode(token));
            app.getEmail().send(email, "Restablece tu contraseña",
                    EmailTemplates.link("Para restablecer tu contraseña visita:", link));
            LOGGER.log(Level.INFO, "Enlace de restablecimiento generado para el usuario {0}",
                    profile.getUser().getId());
        }
        // Mensaje genérico para no revelar si el correo existe.
        setFlash(request, Messages.PASSWORD_RESET_SENT);
        redirect(request, response, "/User?page=forgot-password");
    }

    private void resetPassword(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = param(request, "token");
        if (token.isEmpty() || App.get().getTokens().validatePasswordResetToken(token) == null) {
            request.setAttribute("message", Messages.PASSWORD_RESET_INVALID);
        } else {
            request.setAttribute("token", token);
        }
        forward(request, response, "public/resetPassword.jsp", "Restablecer contraseña");
    }

    private void resetPasswordForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = param(request, "token");
        String newPassword = param(request, "new_password");
        String reNewPassword = param(request, "re_new_password");
        if (!Validation.isNewPassword(newPassword) || !newPassword.equals(reNewPassword)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/User?page=reset-password&token=" + encode(token));
            return;
        }
        App app = App.get();
        TokenService.ResetToken parsed = app.getTokens().validatePasswordResetToken(token);
        // El token incluye el sello de seguridad: si no coincide con el actual, ya se usó o caducó.
        String currentStamp = parsed == null ? null : app.getUsers().getSecurityStamp(parsed.userId());
        if (parsed == null || currentStamp == null || !currentStamp.equals(parsed.securityStamp())) {
            setFlash(request, Messages.PASSWORD_RESET_INVALID);
            redirect(request, response, "/User?page=reset-password");
            return;
        }
        RepositoryResult result = app.getUsers().changePassword(parsed.userId(),
                app.getPasswords().hash(newPassword), UUID.randomUUID().toString());
        if (result == RepositoryResult.SUCCESS) {
            app.getStampCache().invalidate(parsed.userId());
            LOGGER.log(Level.INFO, "Contraseña restablecida para el usuario {0}", parsed.userId());
            setFlash(request, Messages.PASSWORD_RESET_OK);
            redirect(request, response, "/");
            return;
        }
        setFlash(request, Messages.UPDATE_ERROR);
        redirect(request, response, "/User?page=reset-password&token=" + encode(token));
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
