package encuestas.service;

import java.util.regex.Pattern;

/**
 * Validación en servidor de los datos de los formularios. La validación en el navegador
 * (atributos required/maxlength/pattern) es solo una mejora de experiencia de usuario;
 * la política real se aplica aquí.
 */
public final class Validation {

    /** Política de contraseñas: mínimo 6, máximo 50. */
    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 50;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USER_NAME = Pattern.compile("^[0-9a-zA-Z-]+$");

    public static boolean isEmail(String value) {
        return value != null && !value.isEmpty() && value.length() <= 50 && EMAIL.matcher(value).matches();
    }

    /** Sin longitud mínima: cuentas antiguas podrían tener contraseñas más cortas que la política actual. */
    public static boolean isPassword(String value) {
        return hasLength(value, 1, PASSWORD_MAX);
    }

    public static boolean isNewPassword(String value) {
        return hasLength(value, PASSWORD_MIN, PASSWORD_MAX);
    }

    public static boolean isUserName(String value) {
        return hasLength(value, 1, 25) && USER_NAME.matcher(value).matches();
    }

    public static boolean isName(String value) {
        return hasLength(value, 1, 50);
    }

    public static boolean isPollTitle(String value) {
        return hasLength(value, 1, 250);
    }

    public static boolean isPollDescription(String value) {
        return hasLength(value, 1, 500);
    }

    public static boolean isPollPosition(Integer value) {
        return value != null && value >= 1 && value <= 999_999;
    }

    public static boolean isStars(Integer value) {
        return value != null && value >= 1 && value <= 5;
    }

    /** El comentario es opcional. */
    public static boolean isComment(String value) {
        return value == null || value.length() <= 1000;
    }

    public static boolean hasLength(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max;
    }

    public static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Validation() {
    }
}
