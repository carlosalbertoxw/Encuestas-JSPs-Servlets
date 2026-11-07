package encuestas.service;

/** Enmascara datos personales antes de escribirlos en los logs. */
public final class LogRedaction {

    /**
     * {@code demo@encuestas.dev} → {@code d***@encuestas.dev}. Basta para correlacionar
     * intentos sin exponer el correo.
     */
    public static String email(String email) {
        if (email == null || email.isEmpty()) {
            return "";
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    private LogRedaction() {
    }
}
