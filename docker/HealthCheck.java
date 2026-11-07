import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sonda del HEALTHCHECK de Docker (la imagen de Tomcat no trae curl). Se compila en la etapa
 * de build del Dockerfile. Sale con 0 si /health/live responde 200.
 */
public final class HealthCheck {

    public static void main(String[] args) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8080/health/live"))
                    .timeout(Duration.ofSeconds(3)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            System.exit(status == 200 ? 0 : 1);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
