package encuestas.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Genera y valida tokens firmados (HMAC-SHA256) y con caducidad para confirmar el correo
 * y restablecer la contraseña. Son sin estado: no se guardan en la base de datos. El token
 * de restablecimiento incluye el sello de seguridad del usuario, así que queda invalidado
 * en cuanto la contraseña cambia (uso único). Admite varias claves (rotación): firma con
 * la más reciente y valida contra todas las vigentes.
 */
public final class TokenService {

    /** Resultado de validar un token de restablecimiento. */
    public record ResetToken(int userId, String securityStamp) {
    }

    private static final String CONFIRM_PURPOSE = "confirm";
    private static final String RESET_PURPOSE = "reset";
    private static final Duration CONFIRM_LIFETIME = Duration.ofHours(24);
    private static final Duration RESET_LIFETIME = Duration.ofHours(1);
    private static final char SEPARATOR = ':';

    private final java.util.List<byte[]> secrets;

    public TokenService(byte[] secret) {
        this(java.util.List.of(secret));
    }

    /** La primera clave es la activa (firma); el resto solo se usa para validar. */
    public TokenService(java.util.List<byte[]> secrets) {
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una clave de firma");
        }
        this.secrets = secrets.stream().map(byte[]::clone).toList();
    }

    public String createEmailConfirmationToken(int userId) {
        return sign(CONFIRM_PURPOSE + SEPARATOR + userId + SEPARATOR
                + Instant.now().plus(CONFIRM_LIFETIME).toEpochMilli());
    }

    /** Devuelve el id del usuario o null si el token es inválido o expiró. */
    public Integer validateEmailConfirmationToken(String token) {
        String[] parts = openToken(token, CONFIRM_PURPOSE, 3);
        if (parts == null) {
            return null;
        }
        return parseInt(parts[1]);
    }

    public String createPasswordResetToken(int userId, String securityStamp) {
        return sign(RESET_PURPOSE + SEPARATOR + userId + SEPARATOR + securityStamp + SEPARATOR
                + Instant.now().plus(RESET_LIFETIME).toEpochMilli());
    }

    /** Devuelve los datos del token o null si es inválido o expiró. */
    public ResetToken validatePasswordResetToken(String token) {
        String[] parts = openToken(token, RESET_PURPOSE, 4);
        if (parts == null) {
            return null;
        }
        Integer userId = parseInt(parts[1]);
        return userId == null ? null : new ResetToken(userId, parts[2]);
    }

    private String sign(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return encoder.encodeToString(payloadBytes) + "."
                + encoder.encodeToString(hmac(secrets.get(0), payloadBytes));
    }

    /**
     * Verifica firma, propósito, estructura y caducidad; devuelve las partes del payload
     * o null si algo no cuadra (token manipulado, mal formado o expirado).
     */
    private String[] openToken(String token, String purpose, int expectedParts) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot < 1 || dot == token.length() - 1) {
            return null;
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            payloadBytes = decoder.decode(token.substring(0, dot));
            signature = decoder.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!signedByAnyKey(payloadBytes, signature)) {
            return null;
        }
        String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split(String.valueOf(SEPARATOR));
        if (parts.length != expectedParts || !parts[0].equals(purpose)) {
            return null;
        }
        Long expiresAt = parseLong(parts[parts.length - 1]);
        if (expiresAt == null || Instant.now().toEpochMilli() > expiresAt) {
            return null;
        }
        return parts;
    }

    private boolean signedByAnyKey(byte[] payload, byte[] signature) {
        for (byte[] secret : secrets) {
            if (MessageDigest.isEqual(hmac(secret, payload), signature)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] hmac(byte[] secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
