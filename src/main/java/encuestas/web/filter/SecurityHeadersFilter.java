package encuestas.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Cabeceras de seguridad en todas las respuestas, incluida una CSP sin scripts en línea
 * ('unsafe-inline' se mantiene solo en estilos por los que Bootstrap aplica dinámicamente
 * a los menús desplegables). Fuera de Development, las respuestas servidas por HTTPS
 * (directo o vía un proxy de TRUSTED_PROXIES con X-Forwarded-Proto) incluyen HSTS.
 */
public class SecurityHeadersFilter implements Filter {

    private final boolean development;

    public SecurityHeadersFilter(boolean development) {
        this.development = development;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("X-Content-Type-Options", "nosniff");
        http.setHeader("X-Frame-Options", "DENY");
        http.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        http.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data:; frame-ancestors 'none'");
        if (!development && request.isSecure()) {
            http.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        chain.doFilter(request, response);
    }
}
