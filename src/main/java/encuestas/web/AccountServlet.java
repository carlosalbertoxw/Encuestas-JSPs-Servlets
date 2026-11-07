package encuestas.web;

import encuestas.data.RepositoryResult;
import encuestas.infrastructure.App;
import encuestas.model.User;
import encuestas.service.AuthService;
import encuestas.service.EmailTemplates;
import encuestas.service.Messages;
import encuestas.service.PasswordService;
import encuestas.service.TokenService;
import encuestas.service.Validation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Gestión de la cuenta autenticada: perfil, usuario, correo, contraseña y borrado. */
public class AccountServlet extends BaseServlet {

    private static final Logger LOGGER = Logger.getLogger(AccountServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String page = param(request, "page");
        // El enlace de confirmación se abre desde el correo, con o sin sesión.
        if ("confirm-email-change".equals(page)) {
            confirmEmailChange(request, response);
            return;
        }
        if (!AuthService.isAuthenticated(request)) {
            redirect(request, response, "/");
            return;
        }
        switch (page) {
            case "edit-profile" -> forward(request, response, "session/editProfile.jsp", "Editar perfil");
            case "change-user" -> forward(request, response, "session/changeUser.jsp", "Cambiar usuario");
            case "change-email" -> forward(request, response, "session/changeEmail.jsp", "Cambiar correo electrónico");
            case "change-password" -> forward(request, response, "session/changePassword.jsp", "Cambiar contraseña");
            case "delete-account" -> forward(request, response, "session/deleteAccount.jsp", "Borrar cuenta");
            default -> notFound(response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AuthService.isAuthenticated(request)) {
            redirect(request, response, "/");
            return;
        }
        switch (param(request, "form")) {
            case "edit-profile" -> editProfile(request, response);
            case "change-user" -> changeUser(request, response);
            case "change-email" -> changeEmail(request, response);
            case "change-password" -> changePassword(request, response);
            case "delete-account" -> deleteAccount(request, response);
            default -> redirect(request, response, "/");
        }
    }

    private void editProfile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = param(request, "name");
        if (!Validation.isName(name)) {
            fail(request, response, "edit-profile", Messages.VALIDATION_ERROR, Map.of("name", name));
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (App.get().getUsers().updateName(userId, name) == RepositoryResult.SUCCESS) {
            App.get().getAuth().refreshSession(request, userId);
            setFlash(request, Messages.UPDATE_OK);
            redirect(request, response, "/Account?page=edit-profile");
        } else {
            fail(request, response, "edit-profile", Messages.UPDATE_ERROR, Map.of("name", name));
        }
    }

    private void changeUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userName = param(request, "user");
        String password = param(request, "password");
        Map<String, String> old = Map.of("user", userName);
        if (!Validation.isUserName(userName) || !Validation.isPassword(password)) {
            fail(request, response, "change-user", Messages.VALIDATION_ERROR, old);
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            fail(request, response, "change-user", Messages.WRONG_PASSWORD, old);
            return;
        }
        int userId = AuthService.currentUserId(request);
        switch (App.get().getUsers().updateUserName(userId, userName)) {
            case SUCCESS -> {
                App.get().getAuth().refreshSession(request, userId);
                setFlash(request, Messages.UPDATE_OK);
                redirect(request, response, "/Account?page=change-user");
            }
            case DUPLICATE -> fail(request, response, "change-user", Messages.USER_NAME_TAKEN, old);
            default -> fail(request, response, "change-user", Messages.UPDATE_ERROR, old);
        }
    }

    /**
     * Solicita el cambio de correo: el nuevo queda pendiente y solo se aplica cuando se abre
     * el enlace enviado a esa dirección. Se avisa además al correo actual.
     */
    private void changeEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        String password = param(request, "password");
        Map<String, String> old = Map.of("email", email);
        if (!Validation.isEmail(email) || !Validation.isPassword(password)) {
            fail(request, response, "change-email", Messages.VALIDATION_ERROR, old);
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            fail(request, response, "change-email", Messages.WRONG_PASSWORD, old);
            return;
        }
        App app = App.get();
        int userId = AuthService.currentUserId(request);
        String currentEmail = app.getUsers().getUser(userId).getEmail();
        if (email.equalsIgnoreCase(currentEmail)) {
            fail(request, response, "change-email", Messages.SAME_EMAIL, old);
            return;
        }
        if (app.getUsers().getProfileByEmail(email) != null) {
            // No se revela en pantalla que el correo tiene cuenta: solo lo sabe quien controla ese buzón.
            app.getEmail().send(email, "Intento de usar tu correo", EmailTemplates.text(
                    "Alguien intentó cambiar el correo de otra cuenta de Encuestas a esta dirección, pero ya "
                            + "tienes una cuenta con ella. No es necesario que hagas nada."));
        } else if (app.getUsers().setPendingEmail(userId, email) == RepositoryResult.SUCCESS) {
            String link = absoluteUrl("/Account?page=confirm-email-change&token="
                    + UserServlet.encode(app.getTokens().createEmailChangeToken(userId, email)));
            app.getEmail().send(email, "Confirma tu nuevo correo",
                    EmailTemplates.link("Para usar esta dirección en tu cuenta de Encuestas visita:", link));
            app.getEmail().send(currentEmail, "Solicitud de cambio de correo", EmailTemplates.text(
                    "Se solicitó cambiar el correo de tu cuenta de Encuestas. Si no fuiste tú, cambia tu "
                            + "contraseña: el cambio no se aplicará mientras nadie abra el enlace enviado al correo nuevo."));
            LOGGER.log(Level.INFO, "Cambio de correo solicitado por el usuario {0}", userId);
        }
        setFlash(request, Messages.EMAIL_CHANGE_REQUESTED);
        redirect(request, response, "/Account?page=change-email");
    }

    /**
     * Aplica el correo pendiente. Rota el sello de seguridad, así que todas las sesiones
     * (incluida la actual) se cierran y hay que entrar de nuevo con el correo nuevo.
     */
    private void confirmEmailChange(HttpServletRequest request, HttpServletResponse response) throws IOException {
        App app = App.get();
        TokenService.EmailChangeToken change = app.getTokens().validateEmailChangeToken(param(request, "token"));
        RepositoryResult result = change == null ? RepositoryResult.NOT_FOUND
                : app.getUsers().confirmEmailChange(change.userId(), change.email(), UUID.randomUUID().toString());
        if (result == RepositoryResult.SUCCESS) {
            app.getStampCache().invalidate(change.userId());
            app.getAuth().signOut(request);
            LOGGER.log(Level.INFO, "Correo actualizado para el usuario {0}", change.userId());
        }
        setFlash(request, switch (result) {
            case SUCCESS -> Messages.EMAIL_CHANGED;
            case DUPLICATE -> Messages.EMAIL_IN_USE;
            default -> Messages.EMAIL_CHANGE_INVALID;
        });
        redirect(request, response, "/");
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = param(request, "password");
        String newPassword = param(request, "new_password");
        String reNewPassword = param(request, "re_new_password");
        if (!Validation.isPassword(password) || !Validation.isNewPassword(newPassword)
                || !newPassword.equals(reNewPassword)) {
            fail(request, response, "change-password", Messages.VALIDATION_ERROR, Map.of());
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            fail(request, response, "change-password", Messages.WRONG_PASSWORD, Map.of());
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
        redirect(request, response, "/Account?page=change-password");
    }

    private void deleteAccount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = param(request, "password");
        if (!Validation.isPassword(password)) {
            fail(request, response, "delete-account", Messages.VALIDATION_ERROR, Map.of());
            return;
        }
        if (!verifyCurrentPassword(request, password)) {
            fail(request, response, "delete-account", Messages.WRONG_PASSWORD, Map.of());
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
            fail(request, response, "delete-account", Messages.ACCOUNT_DELETE_ERROR, Map.of());
        }
    }

    /** Vuelve al formulario con un mensaje y los valores no sensibles que se escribieron. */
    private void fail(HttpServletRequest request, HttpServletResponse response, String page,
                      String message, Map<String, String> old) throws IOException {
        setFlash(request, message);
        if (!old.isEmpty()) {
            setFlashOld(request, old);
        }
        redirect(request, response, "/Account?page=" + page);
    }

    private boolean verifyCurrentPassword(HttpServletRequest request, String password) {
        User user = App.get().getUsers().getUser(AuthService.currentUserId(request));
        return user != null
                && App.get().getPasswords().verify(user.getPasswordHash(), password) != PasswordService.Outcome.FAILED;
    }
}
