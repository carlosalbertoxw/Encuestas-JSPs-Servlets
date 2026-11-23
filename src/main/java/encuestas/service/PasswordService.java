package encuestas.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashing y verificación de contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria. Cuando el
 * hash guardado usa menos iteraciones que las actuales, señala
 * {@link Outcome#SUCCESS_REHASH_NEEDED} para que el inicio de sesión lo regenere de forma
 * transparente.
 */
public class PasswordService {

    public enum Outcome {
        FAILED,
        SUCCESS,
        /** La contraseña es correcta pero el hash usa parámetros antiguos. */
        SUCCESS_REHASH_NEEDED
    }

    /** Iteraciones recomendadas por OWASP para PBKDF2-HMAC-SHA256. */
    public static final int ITERATIONS = 600_000;
    private static final String PREFIX = "PBKDF2";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    // Hash de una contraseña aleatoria: verificarlo cuesta lo mismo que el de una cuenta real.
    private volatile String dummyHash;

    public String hash(String password) {
        return hash(password, ITERATIONS);
    }

    /** Hash con un número de iteraciones concreto; solo lo usan las pruebas del re-hash. */
    String hash(String password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, iterations);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    public Outcome verify(String storedHash, String password) {
        if (storedHash == null || password == null || !storedHash.startsWith(PREFIX + "$")) {
            return Outcome.FAILED;
        }
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

    /**
     * Hace el mismo trabajo que {@link #verify} cuando la cuenta no existe, para que el tiempo
     * de respuesta no revele qué correos están registrados.
     */
    public void verifyAgainstDummy(String password) {
        String hash = dummyHash;
        if (hash == null) {
            hash = hash(UUID.randomUUID().toString());
            dummyHash = hash;
        }
        verify(hash, password);
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
