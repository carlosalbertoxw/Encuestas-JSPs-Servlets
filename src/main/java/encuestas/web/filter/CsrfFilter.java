package encuestas.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Protección CSRF con token sincronizado: cada sesión recibe un token aleatorio que
 * todos los formularios envían en el campo oculto {@code _csrf}; los POST que no lo
 * presentan (o no coincide) se rechazan con 403.
 */
public class CsrfFilter implements Filter {

    public static final String SESSION_TOKEN = "csrf_token";
    public static final String PARAMETER = "_csrf";

    private final SecureRandom random = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpSession session = http.getSession();
        String token = (String) session.getAttribute(SESSION_TOKEN);
        if (token == null) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(SESSION_TOKEN, token);
        }

        if ("POST".equalsIgnoreCase(http.getMethod())) {
            String sent = http.getParameter(PARAMETER);
            if (sent == null || !MessageDigest.isEqual(
                    token.getBytes(StandardCharsets.UTF_8), sent.getBytes(StandardCharsets.UTF_8))) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Token CSRF inválido");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
