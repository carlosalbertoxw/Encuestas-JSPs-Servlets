package encuestas.web.filter;

import encuestas.infrastructure.JsonLogFormatter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Asigna un identificador a cada petición: se devuelve en la cabecera {@code X-Request-Id} y
 * se incluye en cada línea de log emitida mientras se atiende, para correlacionarlas.
 */
public class RequestIdFilter extends HttpFilter {

    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String requestId = UUID.randomUUID().toString();
        response.setHeader(HEADER, requestId);
        JsonLogFormatter.setRequestId(requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            JsonLogFormatter.clearRequestId();
        }
    }
}
