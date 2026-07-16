package encuestas.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashing y verificación de contraseñas con PBKDF2 (HMAC-SHA256, sal aleatoria).
 * Verifica también los hashes SHA-1 sin sal de las cuentas antiguas y, en ese caso o
 * cuando el hash usa menos iteraciones que las actuales, señala
 * {@link Outcome#SUCCESS_REHASH_NEEDED} para que el inicio de sesión lo regenere
 * de forma transparente.
 */
public class PasswordService {

    public enum Outcome {
        FAILED,
        SUCCESS,
        /** La contraseña es correcta pero el hash usa un esquema o parámetros antiguos. */
        SUCCESS_REHASH_NEEDED
    }

    private static final String PREFIX = "PBKDF2";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    public Outcome verify(String storedHash, String password) {
        if (storedHash == null || password == null) {
            return Outcome.FAILED;
        }
        if (storedHash.startsWith(PREFIX + "$")) {
            return verifyPbkdf2(storedHash, password);
        }
        return verifyLegacySha1(storedHash, password);
    }

    private Outcome verifyPbkdf2(String storedHash, String password) {
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 4) {
                return Outcome.FAILED;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Outcome.FAILED;
            }
            return iterations < ITERATIONS ? Outcome.SUCCESS_REHASH_NEEDED : Outcome.SUCCESS;
        } catch (IllegalArgumentException e) {
            // Hash almacenado corrupto o en un formato desconocido: credencial inválida.
            return Outcome.FAILED;
        }
    }

    /** Cuentas creadas por la versión anterior de la aplicación (SHA-1 hex sin sal). */
    private Outcome verifyLegacySha1(String storedHash, String password) {
        if (storedHash.length() != 40) {
            return Outcome.FAILED;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] actual = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            byte[] expected;
            try {
                expected = HexFormat.of().parseHex(storedHash.toLowerCase());
            } catch (IllegalArgumentException e) {
                return Outcome.FAILED;
            }
            return MessageDigest.isEqual(expected, actual) ? Outcome.SUCCESS_REHASH_NEEDED : Outcome.FAILED;
        } catch (NoSuchAlgorithmException e) {
            return Outcome.FAILED;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BYTES * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 no disponible", e);
        }
    }
}
