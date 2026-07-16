package encuestas.web;

import encuestas.infrastructure.App;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Sonda de salud (readiness/liveness): comprueba la conectividad con la base de datos.
 */
public class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain;charset=UTF-8");
        try (Connection connection = App.get().getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            response.getWriter().print("Healthy");
        } catch (SQLException | IllegalStateException e) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().print("Unhealthy");
        }
    }
}
