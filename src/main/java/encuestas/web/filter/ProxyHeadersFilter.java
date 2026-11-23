package encuestas.web.filter;

import encuestas.infrastructure.TrustedProxies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Tras un proxy inverso de confianza ({@code TRUSTED_PROXIES}), toma la IP del cliente de
 * {@code X-Forwarded-For} y el esquema de {@code X-Forwarded-Proto}, para que el límite de
 * peticiones, HSTS y la cookie {@code Secure} vean la petición original. Si la petición no
 * viene de un proxy de la lista, esas cabeceras se ignoran: cualquier cliente podría falsearlas.
 */
public class ProxyHeadersFilter extends HttpFilter {

    private final transient TrustedProxies trustedProxies;

    public ProxyHeadersFilter(TrustedProxies trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (trustedProxies.isEmpty() || !trustedProxies.contains(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        String clientIp = clientIp(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        String proto = request.getHeader("X-Forwarded-Proto");
        String scheme = "https".equalsIgnoreCase(proto) || "http".equalsIgnoreCase(proto)
                ? proto.toLowerCase(java.util.Locale.ROOT) : request.getScheme();
        chain.doFilter(new ForwardedRequest(request, clientIp, scheme), response);
    }

    /**
     * Recorre {@code X-Forwarded-For} de derecha a izquierda saltando los proxies de confianza:
     * la primera dirección que no lo es corresponde al cliente (las de su izquierda las pudo
     * escribir él mismo).
     */
    private String clientIp(String remoteAddr, String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String[] hops = forwardedFor.split(",");
        String candidate = remoteAddr;
        for (int i = hops.length - 1; i >= 0; i--) {
            candidate = hops[i].trim();
            if (!trustedProxies.contains(candidate)) {
                return candidate;
            }
        }
        return candidate;
    }

    private static final class ForwardedRequest extends HttpServletRequestWrapper {

        private final String remoteAddr;
        private final String scheme;

        ForwardedRequest(HttpServletRequest request, String remoteAddr, String scheme) {
            super(request);
            this.remoteAddr = remoteAddr;
            this.scheme = scheme;
        }

        @Override
        public String getRemoteAddr() {
            return remoteAddr;
        }

        @Override
        public String getRemoteHost() {
            return remoteAddr;
        }

        @Override
        public String getScheme() {
            return scheme;
        }

        @Override
        public boolean isSecure() {
            return "https".equals(scheme);
        }
    }
}
