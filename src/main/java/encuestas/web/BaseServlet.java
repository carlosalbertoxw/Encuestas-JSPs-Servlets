package encuestas.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

/**
 * Utilidades comunes de los servlets: vistas bajo WEB-INF (no accesibles directamente),
 * mensajes flash y valores de formulario (sobreviven a la redirección post-formulario)
 * y respuestas 404.
 */
public abstract class BaseServlet extends HttpServlet {

    private static final String FLASH_ATTRIBUTE = "flash";
    private static final String FLASH_OLD_ATTRIBUTE = "flash_old";

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
        request.setAttribute("old", takeFlashOld(request));
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

    protected String takeFlash(HttpServletRequest request) {
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

    protected void notFound(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    protected String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value != null ? value : "";
    }

    /** URL absoluta de la aplicación para construir los enlaces de los correos. */
    protected String baseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port)
                + request.getContextPath();
    }
}
