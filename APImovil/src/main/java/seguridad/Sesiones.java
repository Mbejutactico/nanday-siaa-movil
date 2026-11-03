package seguridad;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;

/**
 * Sesiones de la app móvil. Al iniciar sesión se entrega un "token" (una clave al azar);
 * la app lo envía en cada pedido (cabecera Authorization: Bearer ...) y así la API sabe quién es.
 *
 * Los permisos son los mismos de la web (Permisos.java): rol + centros del usuario. Se vuelven a
 * leer de la base cada 60 segundos, igual que en la web: si al usuario lo desactivan o le cambian
 * el rol o los centros, la app lo nota enseguida (un usuario desactivado pierde la sesión).
 */
public final class Sesiones {

    /** Datos del usuario que inició sesión. */
    public static class Sesion {
        public final int idUsuario;
        public final String nombre;
        public final String rol;
        /** Email con el que inició sesión (para la bitácora). */
        public final String email;
        long vence;
        private volatile Permisos permisos;

        Sesion(int idUsuario, String nombre, String rol, String email) {
            this.idUsuario = idUsuario;
            this.nombre = nombre;
            this.rol = rol;
            this.email = email;
        }

        /** Permisos vigentes (se releen de la base cada 60 s). */
        public Permisos permisos(Connection conn) throws SQLException {
            Permisos p = permisos;
            if (p == null || p.edadMs() > CACHE_PERMISOS_MS) {
                p = Permisos.cargar(conn, idUsuario);
                permisos = p;
            }
            return p;
        }

        /** Datos con la forma de la sesión web, para el código copiado de los controladores. */
        public SesionDatos comoSesionWeb() {
            return new SesionDatos(idUsuario, nombre);
        }

        // Proveedores e insumos (servlets anteriores): mismas reglas que la web
        public boolean puedeProveedores() {
            return tiene(Permisos.PROVEEDORES);
        }

        public boolean puedeInsumos() {
            return tiene(Permisos.INSUMOS_EDITAR);
        }

        public boolean puedeEliminarInsumos() {
            return tiene(Permisos.INSUMOS_ELIMINAR);
        }

        private boolean tiene(String permiso) {
            Permisos p = permisos;
            if (p != null) {
                return p.tiene(permiso);
            }
            return Permisos.crear(rol, new java.util.HashSet<Integer>()).tiene(permiso);
        }
    }

    private static final long DURACION_MS = 8L * 60 * 60 * 1000;   // 8 horas sin uso
    private static final long CACHE_PERMISOS_MS = 60000L;
    private static final Map<String, Sesion> SESIONES = new ConcurrentHashMap<String, Sesion>();
    private static final SecureRandom AZAR = new SecureRandom();

    private Sesiones() {
    }

    public static String crear(int idUsuario, String nombre, String rol, String email) {
        limpiarVencidas();
        byte[] b = new byte[32];
        AZAR.nextBytes(b);
        StringBuilder token = new StringBuilder();
        for (byte x : b) {
            token.append(String.format("%02x", x));
        }
        Sesion s = new Sesion(idUsuario, nombre, rol, email);
        s.vence = System.currentTimeMillis() + DURACION_MS;
        SESIONES.put(token.toString(), s);
        return token.toString();
    }

    /** Permisos de un rol (sin registrar una sesión): para informarlos al iniciar sesión. */
    public static Sesion permisos(int idUsuario, String nombre, String rol) {
        return new Sesion(idUsuario, nombre, rol, null);
    }

    /** Sesión del pedido, o null si no envió un token válido (o ya venció). */
    public static Sesion de(HttpServletRequest request) {
        String token = token(request);
        if (token == null) {
            return null;
        }
        Sesion s = SESIONES.get(token);
        if (s == null) {
            return null;
        }
        long ahora = System.currentTimeMillis();
        if (s.vence < ahora) {
            SESIONES.remove(token);
            return null;
        }
        s.vence = ahora + DURACION_MS;
        return s;
    }

    /** Cierra la sesión del pedido. Devuelve la sesión cerrada (null si no había). */
    public static Sesion cerrar(HttpServletRequest request) {
        String token = token(request);
        return token == null ? null : SESIONES.remove(token);
    }

    /** Quita de memoria las sesiones vencidas (se llama al crear una nueva). */
    private static void limpiarVencidas() {
        long ahora = System.currentTimeMillis();
        for (Iterator<Sesion> it = SESIONES.values().iterator(); it.hasNext();) {
            if (it.next().vence < ahora) {
                it.remove();
            }
        }
    }

    private static String token(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) {
            return null;
        }
        return h.substring(7).trim();
    }
}
