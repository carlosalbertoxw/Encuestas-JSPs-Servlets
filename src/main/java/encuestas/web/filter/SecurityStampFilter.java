package encuestas.web.filter;

import encuestas.data.UserRepository;
import encuestas.service.AuthService;
import encuestas.service.SecurityStampCache;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Rechaza la sesión si su sello de seguridad no coincide con el de la base de datos
 * (p. ej. tras un cambio de contraseña en otra sesión). El sello se consulta a través
 * de una caché con TTL corto para no golpear la base de datos en cada petición.
 */
public class SecurityStampFilter implements Filter {

    private final UserRepository users;
    private final SecurityStampCache stampCache;

    public SecurityStampFilter(UserRepository users, SecurityStampCache stampCache) {
        this.users = users;
        this.stampCache = stampCache;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpSession session = http.getSession(false);
        if (session != null && session.getAttribute(AuthService.SESSION_ID) != null) {
            int userId = (Integer) session.getAttribute(AuthService.SESSION_ID);
            String sessionStamp = (String) session.getAttribute(AuthService.SESSION_STAMP);
            String currentStamp = stampCache.get(userId, users::getSecurityStamp);
            if (currentStamp == null || !currentStamp.equals(sessionStamp)) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
