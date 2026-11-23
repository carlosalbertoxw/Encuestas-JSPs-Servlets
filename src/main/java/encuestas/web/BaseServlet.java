package encuestas.web;

import encuestas.infrastructure.App;
import encuestas.service.AuthService;
import encuestas.service.Messages;
import encuestas.service.RateLimiter;
import encuestas.service.Validation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Base de los servlets: una tabla de rutas {@code /{Controlador}/{Accion}/{id?}} (cada servlet
 * atiende un controlador y registra sus acciones en el constructor), el límite de peticiones por
 * acción y utilidades comunes: vistas bajo WEB-INF, mensajes flash, valores de formulario que
 * sobreviven a la redirección y respuestas 404.
 */
public abstract class BaseServlet extends HttpServlet {

    /** Una acción del controlador; {@code id} es el tercer segmento de la ruta (0 si no hay). */
    @FunctionalInterface
    protected interface Action {
        void handle(HttpServletRequest request, HttpServletResponse response, int id)
                throws ServletException, IOException;
    }

    /** Límite de peticiones que se aplica a una acción. */
    protected enum Limit {
        NONE,
        /** Formularios de acceso, por IP. */
        AUTH,
        /** Solicitudes de cambio de correo, por usuario: cada una envía correos a terceros. */
        EMAIL_CHANGE
    }

    private record Route(Action action, Limit limit) {
    }

    private static final int TOO_MANY_REQUESTS = 429;
    private static final String FLASH_ATTRIBUTE = "flash";
    private static final String FLASH_OLD_ATTRIBUTE = "flash_old";

    protected final transient App app;
    private final transient Map<String, Route> routes = new HashMap<>();

    protected BaseServlet(App app) {
        this.app = app;
    }

    protected final void get(String action, Action handler) {
        routes.put(key("GET", action), new Route(handler, Limit.NONE));
    }

    protected final void post(String action, Action handler) {
        post(action, handler, Limit.NONE);
    }

    protected final void post(String action, Action handler, Limit limit) {
        routes.put(key("POST", action), new Route(handler, limit));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatch("GET", request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatch("POST", request, response);
    }

    private void dispatch(String method, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        String[] segments = pathInfo == null || pathInfo.equals("/")
                ? new String[0] : pathInfo.substring(1).split("/", -1);
        Route route = segments.length > 2 ? null : routes.get(key(method, segments.length == 0 ? "Index" : segments[0]));
        Integer id = segments.length == 2 ? Validation.parseInt(segments[1]) : Integer.valueOf(0);
        if (route == null || id == null || id < 0) {
            notFound(response);
            return;
        }
        if (!withinLimit(route.limit(), request)) {
            response.setStatus(TOO_MANY_REQUESTS);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().print(Messages.TOO_MANY_REQUESTS);
            return;
        }
        route.action().handle(request, response, id);
    }

    private boolean withinLimit(Limit limit, HttpServletRequest request) {
        RateLimiter limiter = switch (limit) {
            case AUTH -> app.getAuthRateLimiter();
            case EMAIL_CHANGE -> app.getEmailChangeRateLimiter();
            case NONE -> null;
        };
        if (limiter == null) {
            return true;
        }
        String key = limit == Limit.EMAIL_CHANGE
                ? String.valueOf(AuthService.currentUserId(request)) : request.getRemoteAddr();
        return limiter.tryAcquire(key);
    }

    private static String key(String method, String action) {
        return method + " " + action.toLowerCase(Locale.ROOT);
    }

    /** Reenvía a una vista de WEB-INF con el título dado y el mensaje flash pendiente. */
    protected void forward(HttpServletRequest request, HttpServletResponse response, String view, String title)
            throws ServletException, IOException {
        request.setAttribute("title", title);
        if (request.getAttribute("message") == null) {
            String flash = takeFlash(request);
            if (flash != null) {
                request.setAttribute("message", flash);
            }
        }
        if (request.getAttribute("old") == null) {
            request.setAttribute("old", takeFlashOld(request));
        }
        request.getRequestDispatcher("/WEB-INF/views/" + view).forward(request, response);
    }

    /**
     * Conserva los valores no sensibles de un formulario rechazado para repoblarlo tras
     * la redirección (nunca contraseñas). Las vistas los leen del mapa {@code old}.
     */
    protected void setFlashOld(HttpServletRequest request, Map<String, String> values) {
        request.getSession().setAttribute(FLASH_OLD_ATTRIBUTE, values);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> takeFlashOld(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Map.of();
        }
        Object old = session.getAttribute(FLASH_OLD_ATTRIBUTE);
        if (old != null) {
            session.removeAttribute(FLASH_OLD_ATTRIBUTE);
            return (Map<String, String>) old;
        }
        return Map.of();
    }

    /** Guarda un mensaje que se mostrará una sola vez tras la próxima redirección. */
    protected void setFlash(HttpServletRequest request, String message) {
        request.getSession().setAttribute(FLASH_ATTRIBUTE, message);
    }

    private String takeFlash(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        String flash = (String) session.getAttribute(FLASH_ATTRIBUTE);
        if (flash != null) {
            session.removeAttribute(FLASH_ATTRIBUTE);
        }
        return flash;
    }

    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    protected void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /** Número de página del parámetro {@code p} (1 si falta o no es válido). */
    protected int pageNumber(HttpServletRequest request) {
        Integer page = Validation.parseInt(request.getParameter("p"));
        return page == null ? 1 : page;
    }

    /** Campo de texto sin espacios al principio ni al final ("" si falta), para que "   " no pase por un valor. */
    protected String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value != null ? value.trim() : "";
    }

    /** Contraseña tal como se escribió: los espacios forman parte de ella. */
    protected String password(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value != null ? value : "";
    }
}
