package encuestas.infrastructure;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lista de proxies inversos de confianza (variable {@code TRUSTED_PROXIES}: IPs o rangos CIDR
 * separados por comas, p. ej. {@code 10.0.0.0/8,127.0.0.1}). Solo las peticiones que llegan
 * desde uno de ellos pueden indicar con {@code X-Forwarded-For}/{@code X-Forwarded-Proto} la IP
 * y el esquema originales; sin la lista, esas cabeceras se ignoran.
 */
public final class TrustedProxies {

    // Solo literales IP: un nombre de host obligaría a resolverlo por DNS en cada petición.
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

    private record Range(byte[] network, int prefixLength) {
    }

    private final List<Range> ranges;

    private TrustedProxies(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    public static TrustedProxies parse(String value) {
        List<Range> ranges = new ArrayList<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int slash = trimmed.indexOf('/');
            String address = slash < 0 ? trimmed : trimmed.substring(0, slash);
            byte[] bytes = toBytes(address);
            if (bytes == null) {
                throw new IllegalStateException("TRUSTED_PROXIES contiene una dirección no válida: " + trimmed);
            }
            int prefix = bytes.length * 8;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(trimmed.substring(slash + 1));
                } catch (NumberFormatException e) {
                    prefix = -1;
                }
                if (prefix < 0 || prefix > bytes.length * 8) {
                    throw new IllegalStateException("TRUSTED_PROXIES contiene un prefijo no válido: " + trimmed);
                }
            }
            ranges.add(new Range(bytes, prefix));
        }
        return new TrustedProxies(ranges);
    }

    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    public boolean contains(String address) {
        byte[] bytes = toBytes(address);
        if (bytes == null) {
            return false;
        }
        for (Range range : ranges) {
            if (range.network().length == bytes.length && matches(range, bytes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(Range range, byte[] address) {
        int fullBytes = range.prefixLength() / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (range.network()[i] != address[i]) {
                return false;
            }
        }
        int remainingBits = range.prefixLength() % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits) & 0xFF;
        return (range.network()[fullBytes] & mask) == (address[fullBytes] & mask);
    }

    private static byte[] toBytes(String address) {
        if (address == null || !IP_LITERAL.matcher(address).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
