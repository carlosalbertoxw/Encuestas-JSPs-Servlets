package encuestas.web;

import encuestas.data.RepositoryResult;
import encuestas.infrastructure.App;
import encuestas.service.AuthService;
import encuestas.service.Messages;
import encuestas.service.Validation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Gestión de la cuenta autenticada ({@code /Account/...}): perfil, usuario, correo,
 * contraseña y borrado. {@code AuthFilter} exige la sesión salvo en ConfirmEmailChange.
 */
public class AccountServlet extends BaseServlet {

    public AccountServlet(App app) {
        super(app);
        get("EditProfile", (request, response, id) -> forward(request, response, "session/editProfile.jsp", "Editar perfil"));
        get("ChangeUser", (request, response, id) -> forward(request, response, "session/changeUser.jsp", "Cambiar usuario"));
        get("ChangeEmail", (request, response, id) ->
                forward(request, response, "session/changeEmail.jsp", "Cambiar correo electrónico"));
        get("ChangePassword", (request, response, id) ->
                forward(request, response, "session/changePassword.jsp", "Cambiar contraseña"));
        get("DeleteAccount", (request, response, id) -> forward(request, response, "session/deleteAccount.jsp", "Borrar cuenta"));
        get("ConfirmEmailChange", (request, response, id) -> confirmEmailChange(request, response));
        post("EditProfile", (request, response, id) -> editProfile(request, response));
        post("ChangeUser", (request, response, id) -> changeUser(request, response));
        // Limitado por usuario: cada solicitud envía correos.
        post("ChangeEmail", (request, response, id) -> changeEmail(request, response), Limit.EMAIL_CHANGE);
        post("ChangePassword", (request, response, id) -> changePassword(request, response));
        post("DeleteAccount", (request, response, id) -> deleteAccount(request, response));
    }

    private void editProfile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = param(request, "name");
        if (!Validation.isName(name)) {
            fail(request, response, "EditProfile", Messages.VALIDATION_ERROR, Map.of("name", name));
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (app.getUsers().updateName(userId, name) == RepositoryResult.SUCCESS) {
            app.getAuth().refreshSession(request, userId);
            setFlash(request, Messages.UPDATE_OK);
            redirect(request, response, "/Account/EditProfile");
        } else {
            fail(request, response, "EditProfile", Messages.UPDATE_ERROR, Map.of("name", name));
        }
    }

    private void changeUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userName = param(request, "user");
        String password = password(request, "password");
        Map<String, String> old = Map.of("user", userName);
        if (!Validation.isUserName(userName) || !Validation.isPassword(password)) {
            fail(request, response, "ChangeUser", Messages.VALIDATION_ERROR, old);
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (!app.getAccounts().verifyPassword(userId, password)) {
            fail(request, response, "ChangeUser", Messages.WRONG_PASSWORD, old);
            return;
        }
        switch (app.getUsers().updateUserName(userId, userName)) {
            case SUCCESS -> {
                app.getAuth().refreshSession(request, userId);
                setFlash(request, Messages.UPDATE_OK);
                redirect(request, response, "/Account/ChangeUser");
            }
            case DUPLICATE -> fail(request, response, "ChangeUser", Messages.USER_NAME_TAKEN, old);
            default -> fail(request, response, "ChangeUser", Messages.UPDATE_ERROR, old);
        }
    }

    /**
     * Solicita el cambio de correo: el nuevo queda pendiente y solo se aplica cuando se abre
     * el enlace enviado a esa dirección. Se avisa además al correo actual.
     */
    private void changeEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        String password = password(request, "password");
        Map<String, String> old = Map.of("email", email);
        if (!Validation.isEmail(email) || !Validation.isPassword(password)) {
            fail(request, response, "ChangeEmail", Messages.VALIDATION_ERROR, old);
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (!app.getAccounts().verifyPassword(userId, password)) {
            fail(request, response, "ChangeEmail", Messages.WRONG_PASSWORD, old);
            return;
        }
        String currentEmail = app.getUsers().getUser(userId).getEmail();
        if (email.equalsIgnoreCase(currentEmail)) {
            fail(request, response, "ChangeEmail", Messages.SAME_EMAIL, old);
            return;
        }
        app.getAccounts().requestEmailChange(userId, currentEmail, email);
        setFlash(request, Messages.EMAIL_CHANGE_REQUESTED);
        redirect(request, response, "/Account/ChangeEmail");
    }

    /**
     * Aplica el correo pendiente. Rota el sello de seguridad, así que todas las sesiones
     * (incluida la actual) se cierran y hay que entrar de nuevo con el correo nuevo.
     */
    private void confirmEmailChange(HttpServletRequest request, HttpServletResponse response) throws IOException {
        RepositoryResult result = app.getAccounts().confirmEmailChange(param(request, "token"));
        if (result == RepositoryResult.SUCCESS) {
            app.getAuth().signOut(request);
        }
        setFlash(request, switch (result) {
            case SUCCESS -> Messages.EMAIL_CHANGED;
            case DUPLICATE -> Messages.EMAIL_IN_USE;
            default -> Messages.EMAIL_CHANGE_INVALID;
        });
        redirect(request, response, "/User/Index");
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = password(request, "password");
        String newPassword = password(request, "new_password");
        if (!Validation.isPassword(password) || !Validation.isPassword(newPassword)
                || !newPassword.equals(password(request, "re_new_password"))) {
            fail(request, response, "ChangePassword", Messages.VALIDATION_ERROR, Map.of());
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (!app.getAccounts().verifyPassword(userId, password)) {
            fail(request, response, "ChangePassword", Messages.WRONG_PASSWORD, Map.of());
            return;
        }
        // El sello rota y cierra las otras sesiones; la actual se reemite con el sello nuevo.
        if (app.getAccounts().changePassword(userId, newPassword)) {
            app.getAuth().refreshSession(request, userId);
            setFlash(request, Messages.UPDATE_OK);
        } else {
            setFlash(request, Messages.UPDATE_ERROR);
        }
        redirect(request, response, "/Account/ChangePassword");
    }

    private void deleteAccount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = password(request, "password");
        if (!Validation.isPassword(password)) {
            fail(request, response, "DeleteAccount", Messages.VALIDATION_ERROR, Map.of());
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (!app.getAccounts().verifyPassword(userId, password)) {
            fail(request, response, "DeleteAccount", Messages.WRONG_PASSWORD, Map.of());
            return;
        }
        if (app.getAccounts().deleteAccount(userId)) {
            app.getAuth().signOut(request);
            setFlash(request, Messages.ACCOUNT_DELETED);
            redirect(request, response, "/User/Index");
        } else {
            fail(request, response, "DeleteAccount", Messages.ACCOUNT_DELETE_ERROR, Map.of());
        }
    }

    /** Vuelve al formulario con un mensaje y los valores no sensibles que se escribieron. */
    private void fail(HttpServletRequest request, HttpServletResponse response, String action,
                      String message, Map<String, String> old) throws IOException {
        setFlash(request, message);
        if (!old.isEmpty()) {
            setFlashOld(request, old);
        }
        redirect(request, response, "/Account/" + action);
    }
}
