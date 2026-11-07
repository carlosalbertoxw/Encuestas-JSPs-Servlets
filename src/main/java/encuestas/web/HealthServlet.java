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
 * Sondas de salud. {@code /health/live} solo confirma que la aplicación responde (sin BD, para
 * que una caída de MySQL no provoque reinicios en bucle); {@code /health/ready} comprueba
 * además la conectividad con la base de datos.
 */
public class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain;charset=UTF-8");
        boolean healthy = !"/health/ready".equals(request.getServletPath()) || databaseResponds();
        if (!healthy) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        response.getWriter().print(healthy ? "Healthy" : "Unhealthy");
    }

    private static boolean databaseResponds() {
        try (Connection connection = App.get().getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException | IllegalStateException e) {
            return false;
        }
    }
}
