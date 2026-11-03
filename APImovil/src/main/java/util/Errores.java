// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package util;

import java.sql.SQLException;
import javax.servlet.ServletContext;

/**
 * Convierte una excepción en el mensaje que ve el usuario.
 *
 *  - Validaciones del sistema (IllegalArgumentException): se muestra su mensaje tal cual
 *    ("Stock insuficiente de ...", "Seleccione el almacén ...").
 *  - Errores de la base conocidos (dato repetido, registro en uso, texto largo...):
 *    un mensaje claro, sin nombres de tablas ni restricciones.
 *  - Cualquier otro error: un mensaje genérico. El detalle técnico completo queda en el
 *    log del servidor (en Tomcat: logs/localhost.AAAA-MM-DD.log) para poder revisarlo.
 */
public final class Errores {

    public static final String GENERICO =
            "Ocurrió un error inesperado. Intente de nuevo; si el problema continúa, avise al administrador.";

    private Errores() {
    }

    public static String mensaje(ServletContext ctx, Throwable e) {
        if (e == null) {
            return GENERICO;
        }
        if (e instanceof NumberFormatException) {
            return "Hay un dato numérico inválido. Revise los valores ingresados.";
        }
        if (e instanceof IllegalArgumentException) {
            String m = e.getMessage();
            return m == null || m.trim().isEmpty() ? "Datos inválidos." : m;
        }
        SQLException sql = sqlDe(e);
        if (sql != null) {
            String estado = sql.getSQLState();
            // SQLException creada por el propio sistema (sin código de la base): su mensaje es para el usuario
            if (estado == null && sql == e && sql.getMessage() != null) {
                return sql.getMessage();
            }
            if ("23505".equals(estado)) {
                log(ctx, e);
                return "Ya existe un registro con esos datos (hay un valor que no se puede repetir).";
            }
            if ("23503".equals(estado)) {
                log(ctx, e);
                return "No se puede completar la operación: el registro está relacionado con otros datos del sistema. "
                        + "Si ya no se usa, desactívelo en lugar de eliminarlo.";
            }
            if ("23502".equals(estado)) {
                log(ctx, e);
                return "Falta completar un dato obligatorio.";
            }
            if ("23514".equals(estado)) {
                log(ctx, e);
                return "Algún dato no cumple las reglas del sistema (por ejemplo, una cantidad negativa o un estado no válido).";
            }
            if ("22001".equals(estado)) {
                log(ctx, e);
                return "Algún texto ingresado es demasiado largo.";
            }
            if ("22P02".equals(estado) || "22007".equals(estado) || "22008".equals(estado)) {
                log(ctx, e);
                return "Hay un dato con formato inválido (número o fecha). Revise los valores ingresados.";
            }
            if ("40001".equals(estado) || "40P01".equals(estado)) {
                log(ctx, e);
                return "Otro usuario estaba modificando los mismos datos. Intente de nuevo.";
            }
        }
        log(ctx, e);
        return GENERICO;
    }

    private static SQLException sqlDe(Throwable e) {
        Throwable t = e;
        for (int i = 0; t != null && i < 5; i++) {
            if (t instanceof SQLException) {
                return (SQLException) t;
            }
            t = t.getCause();
        }
        return null;
    }

    private static void log(ServletContext ctx, Throwable e) {
        if (ctx != null) {
            ctx.log("Error mostrado al usuario como mensaje genérico", e);
        }
    }
}
