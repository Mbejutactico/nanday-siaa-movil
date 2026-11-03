// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package util;

/**
 * Número de serie provisorio para un activo solicitado que todavía no llegó.
 * Se reemplaza por el número de serie real en la recepción de la OC.
 *
 * Está en una clase Java (compilada por Maven) y no dentro de la JSP porque el
 * compilador de JSP de Tomcat 8.5 no puede leer java.lang.System de Java 21
 * (ClassFormatException) y la JSP completa dejaba de compilar.
 */
public final class Seriales {

    private Seriales() {
    }

    public static String temporal(int idUsuario, int indice) {
        return "PEND-" + System.currentTimeMillis() + "-" + idUsuario + "-" + indice;
    }
}
