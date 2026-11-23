package encuestas.web.filter;

import encuestas.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Exige sesión iniciada en las áreas de la cuenta, las encuestas y las respuestas: sin sesión
 * redirige a la página de inicio. La excepción es el enlace de confirmación del cambio de
 * correo, que se abre desde el correo con o sin sesión.
 */
public class AuthFilter extends HttpFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/account/confirmemailchange");

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = (request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo()))
                .toLowerCase(java.util.Locale.ROOT);
        if (AuthService.isAuthenticated(request) || PUBLIC_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/User/Index");
    }
}
