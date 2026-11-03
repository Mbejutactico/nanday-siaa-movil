package seguridad;

/**
 * Datos del usuario de la app con la misma forma que la sesión HTTP de la web
 * (session.getAttribute("id_usuario"), "nombre_completo"), para que el código copiado
 * de los controladores web funcione sin cambios.
 */
public final class SesionDatos {

    private final int idUsuario;
    private final String nombreCompleto;

    public SesionDatos(int idUsuario, String nombreCompleto) {
        this.idUsuario = idUsuario;
        this.nombreCompleto = nombreCompleto;
    }

    public Object getAttribute(String nombre) {
        if ("id_usuario".equals(nombre)) {
            return Integer.valueOf(idUsuario);
        }
        if ("nombre_completo".equals(nombre)) {
            return nombreCompleto;
        }
        return null;
    }
}
