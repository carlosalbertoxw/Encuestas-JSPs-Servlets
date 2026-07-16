package encuestas.web.filter;

import encuestas.infrastructure.App;
import encuestas.service.Messages;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Limita por IP los envíos de los formularios de autenticación (inicio de sesión,
 * registro y restablecimiento) para frenar la fuerza bruta y el registro masivo.
 */
public class AuthRateLimitFilter implements Filter {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final Set<String> LIMITED_FORMS = Set.of(
            "sign-in", "sign-up", "forgot-password", "reset-password");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        if ("POST".equalsIgnoreCase(http.getMethod())
                && LIMITED_FORMS.contains(String.valueOf(http.getParameter("form")))
                && !App.get().getAuthRateLimiter().tryAcquire(http.getRemoteAddr())) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(TOO_MANY_REQUESTS);
            httpResponse.setContentType("text/plain;charset=UTF-8");
            httpResponse.getWriter().print(Messages.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }
}
