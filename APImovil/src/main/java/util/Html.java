// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package util;

/**
 * Escapado de salida para evitar XSS.
 *
 * esc(x): para texto dentro de HTML (celdas, opciones, atributos).
 * js(x) : para texto dentro de un string JavaScript, por ejemplo
 *         onclick="abrirModal('...')". El resultado no contiene comillas,
 *         &lt;, &gt; ni &amp;, por lo que tambien es seguro dentro del atributo HTML.
 *
 * Ambos devuelven "" cuando el valor es null.
 */
public final class Html {

    private Html() {
    }

    public static String esc(Object valor) {
        if (valor == null) {
            return "";
        }
        String s = valor.toString();
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String js(Object valor) {
        if (valor == null) {
            return "";
        }
        String s = valor.toString();
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '\'' || c == '"' || c == '<' || c == '>' || c == '&'
                    || c == '/' || c < 0x20 || c == '\u2028' || c == '\u2029') {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
