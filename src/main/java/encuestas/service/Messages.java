package encuestas.service;

/** Mensajes de usuario centralizados para no repetir literales entre servlets. */
public final class Messages {

    public static final String VALIDATION_ERROR = "Ocurrió un error en la validación de los datos";
    public static final String WRONG_PASSWORD = "La contraseña es incorrecta";
    public static final String UPDATE_OK = "Los datos se actualizaron exitosamente";
    public static final String UPDATE_ERROR = "Ocurrió un error al actualizar los datos";

    public static final String INVALID_CREDENTIALS = "Correo o contraseña incorrectos";
    // Mismo mensaje tanto si el correo ya existía como si el registro procedió,
    // para no revelar qué correos tienen cuenta.
    public static final String REGISTER_ACKNOWLEDGED = "Si los datos son válidos, te enviamos un correo para confirmar la cuenta.";
    public static final String USER_NAME_TAKEN = "El nombre de usuario no está disponible";
    public static final String EMAIL_NOT_CONFIRMED = "Debes confirmar tu correo antes de iniciar sesión. Revisa tu bandeja de entrada.";
    public static final String ACCOUNT_LOCKED = "Demasiados intentos fallidos. Intenta de nuevo en unos minutos.";
    public static final String EMAIL_CONFIRMED = "Tu correo fue confirmado. Ya puedes iniciar sesión.";
    public static final String EMAIL_CONFIRMATION_INVALID = "El enlace de confirmación no es válido o expiró.";
    public static final String PASSWORD_RESET_SENT = "Si el correo corresponde a una cuenta, te enviamos un enlace para restablecer la contraseña.";
    public static final String PASSWORD_RESET_OK = "Tu contraseña se restableció. Ya puedes iniciar sesión.";
    public static final String PASSWORD_RESET_INVALID = "El enlace para restablecer la contraseña no es válido o expiró.";
    public static final String TOO_MANY_REQUESTS = "Demasiados intentos. Espera un minuto y vuelve a intentarlo.";

    public static final String POLL_SAVED = "Los datos se guardaron exitosamente";
    public static final String POLL_UPDATED = "Los datos se actualizaron exitosamente";
    public static final String POLL_DELETED = "Los datos se borraron exitosamente";
    public static final String POLL_SAVE_ERROR = "Ocurrió un error al guardar los datos";
    public static final String POLL_DELETE_ERROR = "Ocurrió un error al borrar los datos";

    public static final String ANSWER_SAVED = "La respuesta se guardó exitosamente";
    public static final String ANSWER_ERROR = "Ocurrió un error al guardar la respuesta";
    public static final String ANSWER_DUPLICATE = "Ya habías respondido esta encuesta";

    public static final String ACCOUNT_DELETED = "La cuenta se eliminó exitosamente";
    public static final String ACCOUNT_DELETE_ERROR = "Ocurrió un error al eliminar la cuenta";

    private Messages() {
    }
}
