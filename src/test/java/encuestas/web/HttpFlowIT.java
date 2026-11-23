package encuestas.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Pruebas HTTP de punta a punta: la aplicación real (listener, filtros, servlets y JSP) en un
 * Tomcat embebido, contra MySQL y Mailpit efímeros. Los enlaces de los correos se leen de la API
 * de Mailpit, así que también se prueba la bandeja de salida y el envío por SMTP.
 */
@Testcontainers(disabledWithoutDocker = true)
class HttpFlowIT {

    private static final String PASSWORD = "Secreta123";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final Pattern MAIL_LINK = Pattern.compile("(/(?:User|Account)/[A-Za-z]+\\?token=[A-Za-z0-9._%\\-]+)");
    private static final Pattern MAIL_SUMMARY = Pattern.compile("\"ID\":\"([^\"]+)\".*?\"Subject\":\"([^\"]*)\"");
    private static final List<String> PROPERTIES = List.of("APP_ENV", "DB_HOST", "DB_PORT", "DB_NAME", "DB_USER",
            "DB_PASSWORD", "SMTP_HOST", "SMTP_PORT", "SMTP_STARTTLS", "APP_KEYS_DIR", "APP_BASE_URL",
            "RATE_LIMIT_AUTH", "RATE_LIMIT_EMAIL_CHANGE");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("encuestas")
            .withUsername("encuestas")
            .withPassword("encuestas");

    @Container
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.34")
            .withExposedPorts(1025, 8025);

    private static Tomcat tomcat;
    private static String baseUrl;
    private static String mailpitUrl;
    private static Path workDir;

    @BeforeAll
    static void startApplication() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        baseUrl = "http://localhost:" + port;
        mailpitUrl = "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
        workDir = Files.createTempDirectory("encuestas-http");

        System.setProperty("APP_ENV", "Development");
        System.setProperty("DB_HOST", MYSQL.getHost());
        System.setProperty("DB_PORT", String.valueOf(MYSQL.getFirstMappedPort()));
        System.setProperty("DB_NAME", "encuestas");
        System.setProperty("DB_USER", "encuestas");
        System.setProperty("DB_PASSWORD", "encuestas");
        System.setProperty("SMTP_HOST", MAILPIT.getHost());
        System.setProperty("SMTP_PORT", String.valueOf(MAILPIT.getMappedPort(1025)));
        System.setProperty("SMTP_STARTTLS", "false");
        System.setProperty("APP_KEYS_DIR", workDir.resolve("keys").toString());
        System.setProperty("APP_BASE_URL", baseUrl);
        // Todas las pruebas salen de la misma IP: se amplían los límites para que no se bloqueen.
        System.setProperty("RATE_LIMIT_AUTH", "10000");
        System.setProperty("RATE_LIMIT_EMAIL_CHANGE", "10000");

        tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(workDir.resolve("tomcat").toString());
        tomcat.getConnector();
        Context context = tomcat.addWebapp("", new File("src/main/webapp").getAbsolutePath());
        WebResourceRoot resources = new StandardRoot(context);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
                new File("target/classes").getAbsolutePath(), "/"));
        context.setResources(resources);
        tomcat.start();
    }

    @AfterAll
    static void stopApplication() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
        PROPERTIES.forEach(System::clearProperty);
    }

    @Test
    void healthProbesRespond() throws Exception {
        Browser browser = new Browser();
        assertEquals("Healthy", browser.get("/health/live").body());
        assertEquals("Healthy", browser.get("/health/ready").body());
    }

    @Test
    void protectedRoutesRedirectToHomeWithoutSession() throws Exception {
        HttpResponse<String> response = new Browser().get("/Poll/Index");
        assertEquals(302, response.statusCode());
        assertTrue(location(response).endsWith("/User/Index"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        Browser browser = new Browser();
        browser.get("/");
        HttpResponse<String> response = browser.postRaw("/User/Login", Map.of("email", "a@b.cd", "password", PASSWORD));
        assertEquals(403, response.statusCode());
    }

    @Test
    void registerConfirmAndSignIn() throws Exception {
        String email = newEmail();
        Browser browser = new Browser();
        register(browser, email);
        String link = waitForLink(email, "Confirma tu cuenta", 1);

        // Antes de confirmar, el login se rechaza con un aviso.
        assertTrue(location(login(browser, email, PASSWORD)).endsWith("/User/Index"));
        assertTrue(browser.get("/User/Index").body().contains("Debes confirmar tu correo"));

        assertEquals(302, browser.get(link).statusCode());
        assertTrue(location(login(browser, email, PASSWORD)).endsWith("/Poll/Index"));
    }

    @Test
    void resendConfirmationSendsANewWorkingLink() throws Exception {
        String email = newEmail();
        Browser browser = new Browser();
        register(browser, email);
        waitForLink(email, "Confirma tu cuenta", 1);

        browser.post("/User/ResendConfirmation", "/User/ResendConfirmation", Map.of("email", email));
        assertTrue(browser.get("/User/ResendConfirmation").body().contains("te enviamos un enlace nuevo"));

        browser.get(waitForLink(email, "Confirma tu cuenta", 2));
        assertTrue(location(login(browser, email, PASSWORD)).endsWith("/Poll/Index"));
    }

    @Test
    void passwordResetChangesThePasswordAndTheLinkWorksOnce() throws Exception {
        String email = createConfirmedAccount();
        Browser browser = new Browser();
        browser.post("/User/ForgotPassword", "/User/ForgotPassword", Map.of("email", email));
        String link = waitForLink(email, "Restablece tu contraseña", 1);
        String token = URLDecoder.decode(link.substring(link.indexOf("token=") + 6), StandardCharsets.UTF_8);

        Map<String, String> form = Map.of("token", token, "new_password", "Nueva12345", "re_new_password", "Nueva12345");
        assertTrue(location(browser.post(link, "/User/ResetPassword", form)).endsWith("/User/Index"));
        assertTrue(location(login(new Browser(), email, "Nueva12345")).endsWith("/Poll/Index"));

        // El mismo enlace ya no sirve: el sello de seguridad cambió.
        browser.post(link, "/User/ResetPassword", form);
        assertTrue(browser.get("/User/ResetPassword").body().contains("no es válido o expiró"));
    }

    @Test
    void emailChangeAppliesWhenTheNewAddressConfirms() throws Exception {
        String email = createConfirmedAccount();
        String newEmail = newEmail();
        Browser browser = new Browser();
        assertTrue(location(login(browser, email, PASSWORD)).endsWith("/Poll/Index"));

        browser.post("/Account/ChangeEmail", "/Account/ChangeEmail", Map.of("email", newEmail, "password", PASSWORD));
        waitForLink(email, "Solicitud de cambio de correo", 1);
        String link = waitForLink(newEmail, "Confirma tu nuevo correo", 1);

        assertEquals(302, browser.get(link).statusCode());
        assertTrue(location(login(new Browser(), newEmail, PASSWORD)).endsWith("/Poll/Index"));
        assertTrue(location(login(new Browser(), email, PASSWORD)).endsWith("/User/Index"));
    }

    @Test
    void invalidPollRedisplaysTheFormWithTheInput() throws Exception {
        Browser browser = new Browser();
        assertTrue(location(login(browser, "demo@encuestas.dev", "demo1234")).endsWith("/Poll/Index"));

        HttpResponse<String> response = browser.post("/Poll/Add", "/Poll/Add",
                Map.of("title", "   ", "description", "Texto que no debe perderse", "position", "1"));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Texto que no debe perderse"));
    }

    @Test
    void unknownRoutesReturn404() throws Exception {
        assertEquals(404, new Browser().get("/User/NoExiste").statusCode());
        assertEquals(404, new Browser().get("/User/Index/1/2").statusCode());
    }

    // --- Utilidades ---

    private static String newEmail() {
        return "flujo-" + UUID.randomUUID().toString().replace("-", "") + "@test.com";
    }

    private static String createConfirmedAccount() throws Exception {
        String email = newEmail();
        Browser browser = new Browser();
        register(browser, email);
        browser.get(waitForLink(email, "Confirma tu cuenta", 1));
        return email;
    }

    private static void register(Browser browser, String email) throws Exception {
        HttpResponse<String> response = browser.post("/User/Index", "/User/Register",
                Map.of("email", email, "password", PASSWORD, "re_password", PASSWORD));
        assertEquals(302, response.statusCode());
    }

    private static HttpResponse<String> login(Browser browser, String email, String password) throws Exception {
        return browser.post("/User/Index", "/User/Login", Map.of("email", email, "password", password));
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    /**
     * Espera a que Mailpit reciba {@code count} correos con ese asunto para el destinatario y
     * devuelve la ruta (con su token) del enlace del más reciente.
     */
    private static String waitForLink(String to, String subject, int count) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            String query = URLEncoder.encode("to:" + to, StandardCharsets.UTF_8);
            String list = client.send(HttpRequest.newBuilder(URI.create(mailpitUrl + "/api/v1/search?query=" + query)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            List<String> ids = new ArrayList<>();
            Matcher matcher = MAIL_SUMMARY.matcher(list);
            while (matcher.find()) {
                if (matcher.group(2).equals(subject)) {
                    ids.add(matcher.group(1));
                }
            }
            if (ids.size() >= count) {
                // Mailpit lista primero el más reciente.
                String message = client.send(HttpRequest.newBuilder(URI.create(mailpitUrl + "/api/v1/message/" + ids.get(0))).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
                Matcher link = MAIL_LINK.matcher(message);
                return link.find() ? link.group(1) : "";
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No llegó el correo '" + subject + "' a " + to);
    }

    /** Cliente HTTP con cookies propias (una sesión), sin seguir redirecciones. */
    private static final class Browser {

        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** Abre {@code formPage} para obtener el token CSRF y envía el formulario a {@code action}. */
        HttpResponse<String> post(String formPage, String action, Map<String, String> fields)
                throws IOException, InterruptedException {
            Matcher matcher = CSRF.matcher(get(formPage).body());
            Map<String, String> form = new LinkedHashMap<>(fields);
            form.put("_csrf", matcher.find() ? matcher.group(1) : "");
            return postRaw(action, form);
        }

        HttpResponse<String> postRaw(String action, Map<String, String> form) throws IOException, InterruptedException {
            String body = form.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            return client.send(HttpRequest.newBuilder(URI.create(baseUrl + action))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }
}
