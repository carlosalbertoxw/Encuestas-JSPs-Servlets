package encuestas.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

class InfrastructureTest {

    @Test
    void baseUrlMustBeAnAbsoluteHttpUrl() {
        assertEquals("https://encuestas.example.com", AppConfig.parseBaseUrl("https://encuestas.example.com/"));
        assertThrows(IllegalStateException.class, () -> AppConfig.parseBaseUrl(""));
        assertThrows(IllegalStateException.class, () -> AppConfig.parseBaseUrl("no-es-url"));
        assertThrows(IllegalStateException.class, () -> AppConfig.parseBaseUrl("ftp://encuestas.example.com"));
    }

    @Test
    void portsMustBeValidNumbers() {
        assertEquals(3306, AppConfig.parsePort("ENCUESTAS_TEST_PORT_UNSET", "3306"));
        System.setProperty("ENCUESTAS_TEST_PORT", "no-es-numero");
        try {
            assertThrows(IllegalStateException.class, () -> AppConfig.parsePort("ENCUESTAS_TEST_PORT", "3306"));
            System.setProperty("ENCUESTAS_TEST_PORT", "70000");
            assertThrows(IllegalStateException.class, () -> AppConfig.parsePort("ENCUESTAS_TEST_PORT", "3306"));
        } finally {
            System.clearProperty("ENCUESTAS_TEST_PORT");
        }
    }

    @Test
    void migrationsAreDiscoveredInOrder() {
        List<String> scripts = MigrationRunner.discoverScripts();
        assertEquals("V0001__esquema_inicial.sql", scripts.get(0));
        assertEquals(scripts.stream().sorted().toList(), scripts);
        assertTrue(scripts.contains("V0002__correo_pendiente.sql"));
        assertTrue(scripts.contains("V0003__outbox_y_contadores.sql"));
    }

    @Test
    void trustedProxiesMatchAddressesAndRanges() {
        TrustedProxies proxies = TrustedProxies.parse("10.0.0.0/8, 192.168.1.10, fd00::/8");
        assertTrue(proxies.contains("10.20.30.40"));
        assertTrue(proxies.contains("192.168.1.10"));
        assertTrue(proxies.contains("fd12::1"));
        assertFalse(proxies.contains("192.168.1.11"));
        assertFalse(proxies.contains("172.17.0.1"));
        assertFalse(proxies.contains("no-es-ip"));
    }

    @Test
    void withoutTrustedProxiesNothingIsTrusted() {
        TrustedProxies proxies = TrustedProxies.parse("");
        assertTrue(proxies.isEmpty());
        assertFalse(proxies.contains("127.0.0.1"));
        assertThrows(IllegalStateException.class, () -> TrustedProxies.parse("proxy.local"));
        assertThrows(IllegalStateException.class, () -> TrustedProxies.parse("10.0.0.0/40"));
    }

    @Test
    void jsonLogsAreEscapedAndIncludeTheRequestId() {
        JsonLogFormatter.setRequestId("req-1");
        try {
            LogRecord record = new LogRecord(Level.INFO, "Hola \"mundo\"\nadiós");
            record.setLoggerName("encuestas.prueba");
            String json = new JsonLogFormatter().format(record);
            assertTrue(json.contains("\"message\":\"Hola \\\"mundo\\\"\\nadiós\""), json);
            assertTrue(json.contains("\"requestId\":\"req-1\""), json);
            assertTrue(json.endsWith("}\n"));
        } finally {
            JsonLogFormatter.clearRequestId();
        }
    }
}
