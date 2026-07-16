package encuestas.web;

import encuestas.data.RepositoryResult;
import encuestas.data.UserRepository;
import encuestas.infrastructure.App;
import encuestas.model.User;
import encuestas.model.UserProfile;
import encuestas.service.AuthService;
import encuestas.service.Messages;
import encuestas.service.PasswordService;
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
 * Cuenta y acceso: inicio, registro con confirmación de correo, inicio y cierre de
 * sesión, restablecimiento de contraseña, gestión de la cuenta y perfil público.
 */
public class UserServlet extends BaseServlet {

    private static final Logger LOGGER = Logger.getLogger(UserServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String page = param(request, "page");
        String profile = param(request, "profile");

        // Páginas de cuenta accesibles con o sin sesión (equivalen a "permitir anónimo").
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
            return;
        }

        switch (page) {
            case "" -> redirect(request, response, "/Poll?page=dashboard");
            case "edit-profile" -> forward(request, response, "session/editProfile.jsp", "Editar perfil");
            case "change-user" -> forward(request, response, "session/changeUser.jsp", "Cambiar usuario");
            case "change-email" -> forward(request, response, "session/changeEmail.jsp", "Cambiar correo electrónico");
            case "change-password" -> forward(request, response, "session/changePassword.jsp", "Cambiar contraseña");
            case "delete-account" -> forward(request, response, "session/deleteAccount.jsp", "Borrar cuenta");
            default -> notFound(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String form = param(request, "form");
        if (!AuthService.isAuthenticated(request)) {
            switch (form) {
                case "sign-in" -> signIn(request, response);
                case "sign-up" -> signUp(request, response);
                case "forgot-password" -> forgotPassword(request, response);
                case "reset-password" -> resetPasswordForm(request, response);
                default -> redirect(request, response, "/");
            }
            return;
        }
        switch (form) {
            case "edit-profile" -> editProfileForm(request, response);
            case "change-user" -> changeUserForm(request, response);
            case "change-email" -> changeEmailForm(request, response);
            case "change-password" -> changePasswordForm(request, response);
            case "delete-account" -> deleteAccountForm(request, response);
            case "close-session" -> closeSession(request, response);
            default -> redirect(request, response, "/");
        }
    }

    /** Perfil público por nombre de usuario, con sus encuestas para responderlas. */
    private void profile(HttpServletRequest request, HttpServletResponse response, String profile)
            throws ServletException, IOException {
        if (profile.length() > 25) {
            notFound(request, response);
            return;
        }
        UserProfile userProfile = App.get().getUsers().getProfileByUserName(profile);
        if (userProfile == null) {
            notFound(request, response);
            return;
        }
        request.setAttribute("polls", App.get().getPolls().getPolls(userProfile.getUser().getId()));
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
        AuthService.LoginResult result = App.get().getAuth().login(request, email, password);
        switch (result) {
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
        if (result == RepositoryResult.SUCCESS) {
            UserProfile profile = app.getUsers().getProfileByEmail(email);
            if (profile != null) {
                sendConfirmationEmail(request, email, profile.getUser().getId());
            }
            LOGGER.log(Level.INFO, "Cuenta nueva registrada para {0}", email);
        }
        // Se responde lo mismo exista o no el correo, para no revelar cuentas.
        setFlash(request, Messages.REGISTER_ACKNOWLEDGED);
        redirect(request, response, "/");
    }

    private void confirmEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = param(request, "token");
        Integer userId = App.get().getTokens().validateEmailConfirmationToken(token);
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
            String link = baseUrl(request) + "/User?page=reset-password&token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);
            app.getEmail().send(email, "Restablece tu contraseña",
                    "Para restablecer tu contraseña visita: <a href=\"" + link + "\">" + link + "</a>");
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
            redirect(request, response, "/User?page=reset-password&token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8));
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
        redirect(request, response, "/User?page=reset-password&token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    private void editProfileForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = param(request, "name");
        if (!Validation.isName(name)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("name", name));
            redirect(request, response, "/User?page=edit-profile");
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (App.get().getUsers().updateName(userId, name) == RepositoryResult.SUCCESS) {
            App.get().getAuth().refreshSession(request, userId);
            setFlash(request, Messages.UPDATE_OK);
        } else {
            setFlash(request, Messages.UPDATE_ERROR);
            setFlashOld(request, Map.of("name", name));
        }
        redirect(request, response, "/User?page=edit-profile");
    }

    private void changeUserForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userName = param(request, "user");
        String password = param(request, "password");
        if (!Validation.isUserName(userName) || !Validation.isPassword(password)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("user", userName));
            redirect(request, response, "/User?page=change-user");
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            setFlash(request, Messages.WRONG_PASSWORD);
            setFlashOld(request, Map.of("user", userName));
            redirect(request, response, "/User?page=change-user");
            return;
        }
        int userId = AuthService.currentUserId(request);
        switch (App.get().getUsers().updateUserName(userId, userName)) {
            case SUCCESS -> {
                App.get().getAuth().refreshSession(request, userId);
                setFlash(request, Messages.UPDATE_OK);
            }
            case DUPLICATE -> {
                setFlash(request, Messages.USER_NAME_TAKEN);
                setFlashOld(request, Map.of("user", userName));
            }
            default -> {
                setFlash(request, Messages.UPDATE_ERROR);
                setFlashOld(request, Map.of("user", userName));
            }
        }
        redirect(request, response, "/User?page=change-user");
    }

    private void changeEmailForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        String password = param(request, "password");
        if (!Validation.isEmail(email) || !Validation.isPassword(password)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("email", email));
            redirect(request, response, "/User?page=change-email");
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            setFlash(request, Messages.WRONG_PASSWORD);
            setFlashOld(request, Map.of("email", email));
            redirect(request, response, "/User?page=change-email");
            return;
        }
        int userId = AuthService.currentUserId(request);
        // No se distingue "correo ya registrado" para no revelar cuentas ajenas.
        if (App.get().getUsers().updateEmail(userId, email) == RepositoryResult.SUCCESS) {
            App.get().getAuth().refreshSession(request, userId);
            LOGGER.log(Level.INFO, "Correo actualizado para el usuario {0}", userId);
            setFlash(request, Messages.UPDATE_OK);
        } else {
            setFlash(request, Messages.UPDATE_ERROR);
            setFlashOld(request, Map.of("email", email));
        }
        redirect(request, response, "/User?page=change-email");
    }

    private void changePasswordForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = param(request, "password");
        String newPassword = param(request, "new_password");
        String reNewPassword = param(request, "re_new_password");
        if (!Validation.isPassword(password) || !Validation.isNewPassword(newPassword)
                || !newPassword.equals(reNewPassword)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/User?page=change-password");
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            setFlash(request, Messages.WRONG_PASSWORD);
            redirect(request, response, "/User?page=change-password");
            return;
        }
        int userId = AuthService.currentUserId(request);
        App app = App.get();
        // Rota el sello de seguridad para cerrar otras sesiones; luego reemite la sesión
        // actual con el sello nuevo para no expulsar al propio usuario.
        RepositoryResult result = app.getUsers().changePassword(userId,
                app.getPasswords().hash(newPassword), UUID.randomUUID().toString());
        if (result == RepositoryResult.SUCCESS) {
            app.getAuth().refreshSession(request, userId);
            app.getStampCache().invalidate(userId);
            LOGGER.log(Level.INFO, "Contraseña cambiada por el usuario {0}", userId);
            setFlash(request, Messages.UPDATE_OK);
        } else {
            setFlash(request, Messages.UPDATE_ERROR);
        }
        redirect(request, response, "/User?page=change-password");
    }

    private void deleteAccountForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = param(request, "password");
        if (!Validation.isPassword(password)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/User?page=delete-account");
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            setFlash(request, Messages.WRONG_PASSWORD);
            redirect(request, response, "/User?page=delete-account");
            return;
        }
        int userId = AuthService.currentUserId(request);
        App app = App.get();
        if (app.getUsers().deleteAccount(userId) == RepositoryResult.SUCCESS) {
            app.getAuth().signOut(request);
            app.getStampCache().invalidate(userId);
            LOGGER.log(Level.INFO, "Cuenta eliminada por el usuario {0}", userId);
            setFlash(request, Messages.ACCOUNT_DELETED);
            redirect(request, response, "/");
        } else {
            setFlash(request, Messages.ACCOUNT_DELETE_ERROR);
            redirect(request, response, "/User?page=delete-account");
        }
    }

    private void closeSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        App.get().getAuth().signOut(request);
        redirect(request, response, "/");
    }

    private void sendConfirmationEmail(HttpServletRequest request, String email, int userId) {
        String token = App.get().getTokens().createEmailConfirmationToken(userId);
        String link = baseUrl(request) + "/User?page=confirm-email&token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        App.get().getEmail().send(email, "Confirma tu cuenta",
                "Para confirmar tu cuenta visita: <a href=\"" + link + "\">" + link + "</a>");
    }

    private boolean verifyCurrentPassword(HttpServletRequest request, String password) {
        UserRepository users = App.get().getUsers();
        User user = users.getUser(AuthService.currentUserId(request));
        return user != null
                && App.get().getPasswords().verify(user.getPasswordHash(), password) != PasswordService.Outcome.FAILED;
    }
}
