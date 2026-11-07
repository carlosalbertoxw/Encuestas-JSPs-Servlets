package encuestas.service;

/** Cuerpos HTML de los correos, con el contenido escapado. */
public final class EmailTemplates {

    public static String text(String text) {
        return "<p>" + escape(text) + "</p>";
    }

    public static String link(String text, String url) {
        String href = escape(url);
        return "<p>" + escape(text) + "</p><p><a href=\"" + href + "\">" + href + "</a></p>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private EmailTemplates() {
    }
}
