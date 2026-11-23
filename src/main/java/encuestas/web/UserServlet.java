package encuestas.web;

import encuestas.data.PagedResult;
import encuestas.infrastructure.App;
import encuestas.model.UserProfile;
import encuestas.service.AccountService;
import encuestas.service.AuthService;
import encuestas.service.Messages;
import encuestas.service.Validation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Página de inicio, perfiles públicos y flujos de acceso ({@code /User/...}): inicio de
 * sesión, registro con confirmación de correo, recuperación de contraseña y cierre de sesión.
 * La gestión de la cuenta autenticada vive en {@link AccountServlet}.
 */
public class UserServlet extends BaseServlet {

    private static final String DASHBOARD = "/Poll/Index";

    public UserServlet(App app) {
        super(app);
        get("Index", (request, response, id) -> index(request, response));
        post("Login", (request, response, id) -> login(request, response), Limit.AUTH);
        post("Register", (request, response, id) -> register(request, response), Limit.AUTH);
        get("ConfirmEmail", (request, response, id) -> confirmEmail(request, response));
        get("ResendConfirmation", (request, response, id) ->
                forward(request, response, "public/resendConfirmation.jsp", "Reenviar confirmación"));
        post("ResendConfirmation", (request, response, id) -> resendConfirmation(request, response), Limit.AUTH);
        get("ForgotPassword", (request, response, id) ->
                forward(request, response, "public/forgotPassword.jsp", "Recuperar contraseña"));
        post("ForgotPassword", (request, response, id) -> forgotPassword(request, response), Limit.AUTH);
        get("ResetPassword", (request, response, id) -> resetPasswordForm(request, response));
        post("ResetPassword", (request, response, id) -> resetPassword(request, response), Limit.AUTH);
        post("CloseSession", (request, response, id) -> closeSession(request, response));
    }

    /** Inicio: acceso/registro para visitantes, o el perfil público si se pasa {@code ?profile=}. */
    private void index(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AuthService.isAuthenticated(request)) {
            forward(request, response, "public/home.jsp", "Inicio");
            return;
        }
        String profile = param(request, "profile");
        if (profile.isEmpty()) {
            redirect(request, response, DASHBOARD);
            return;
        }
        UserProfile userProfile = profile.length() > 25 ? null : app.getUsers().getProfileByUserName(profile);
        if (userProfile == null) {
            notFound(response);
            return;
        }
        request.setAttribute("result", app.getPolls().getPolls(userProfile.getUser().getId(),
                pageNumber(request), PagedResult.DEFAULT_PAGE_SIZE));
        request.setAttribute("profileUserName", userProfile.getUserName());
        forward(request, response, "session/profile.jsp",
                userProfile.getName() + " (" + userProfile.getUserName() + ")");
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (AuthService.isAuthenticated(request)) {
            redirect(request, response, DASHBOARD);
            return;
        }
        String email = param(request, "email");
        String password = password(request, "password");
        if (!Validation.isEmail(email) || !Validation.isPassword(password)) {
            rejectLogin(request, response, email, Messages.INVALID_CREDENTIALS);
            return;
        }
        switch (app.getAuth().login(request, email, password)) {
            case SUCCESS -> redirect(request, response, DASHBOARD);
            case EMAIL_NOT_CONFIRMED -> rejectLogin(request, response, email, Messages.EMAIL_NOT_CONFIRMED);
            case LOCKED_OUT -> rejectLogin(request, response, email, Messages.ACCOUNT_LOCKED);
            default -> rejectLogin(request, response, email, Messages.INVALID_CREDENTIALS);
        }
    }

    private void rejectLogin(HttpServletRequest request, HttpServletResponse response,
                             String email, String message) throws IOException {
        setFlash(request, message);
        setFlashOld(request, Map.of("form", "sign-in", "email", email));
        redirect(request, response, "/User/Index");
    }

    private void register(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (AuthService.isAuthenticated(request)) {
            redirect(request, response, DASHBOARD);
            return;
        }
        String email = param(request, "email");
        String password = password(request, "password");
        if (!Validation.isEmail(email) || !Validation.isPassword(password)
                || !password.equals(password(request, "re_password"))) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("form", "sign-up", "email", email));
            redirect(request, response, "/User/Index");
            return;
        }
        app.getAccounts().register(email, password);
        // Se responde lo mismo exista o no el correo, para no revelar cuentas.
        setFlash(request, Messages.REGISTER_ACKNOWLEDGED);
        redirect(request, response, "/User/Index");
    }

    private void confirmEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setFlash(request, app.getAccounts().confirmEmail(param(request, "token"))
                ? Messages.EMAIL_CONFIRMED : Messages.EMAIL_CONFIRMATION_INVALID);
        redirect(request, response, "/User/Index");
    }

    private void resendConfirmation(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        if (!Validation.isEmail(email)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("email", email));
            redirect(request, response, "/User/ResendConfirmation");
            return;
        }
        app.getAccounts().resendConfirmation(email);
        // Mensaje genérico para no revelar si el correo existe ni si ya estaba confirmado.
        setFlash(request, Messages.CONFIRMATION_RESENT);
        redirect(request, response, "/User/ResendConfirmation");
    }

    private void forgotPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = param(request, "email");
        if (!Validation.isEmail(email)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            setFlashOld(request, Map.of("email", email));
            redirect(request, response, "/User/ForgotPassword");
            return;
        }
        app.getAccounts().requestPasswordReset(email);
        // Mensaje genérico para no revelar si el correo existe.
        setFlash(request, Messages.PASSWORD_RESET_SENT);
        redirect(request, response, "/User/ForgotPassword");
    }

    private void resetPasswordForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = param(request, "token");
        if (token.isEmpty() || !app.getAccounts().isPasswordResetTokenValid(token)) {
            request.setAttribute("message", Messages.PASSWORD_RESET_INVALID);
        } else {
            request.setAttribute("token", token);
        }
        forward(request, response, "public/resetPassword.jsp", "Restablecer contraseña");
    }

    private void resetPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = param(request, "token");
        String newPassword = password(request, "new_password");
        if (!Validation.isPassword(newPassword) || !newPassword.equals(password(request, "re_new_password"))) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/User/ResetPassword?token=" + encode(token));
            return;
        }
        AccountService.ResetResult result = app.getAccounts().resetPassword(token, newPassword);
        switch (result) {
            case SUCCESS -> {
                setFlash(request, Messages.PASSWORD_RESET_OK);
                redirect(request, response, "/User/Index");
            }
            // Sin token, la página ya muestra que el enlace no es válido.
            case INVALID_TOKEN -> redirect(request, response, "/User/ResetPassword");
            default -> {
                setFlash(request, Messages.UPDATE_ERROR);
                redirect(request, response, "/User/ResetPassword?token=" + encode(token));
            }
        }
    }

    private void closeSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        app.getAuth().signOut(request);
        redirect(request, response, "/User/Index");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
