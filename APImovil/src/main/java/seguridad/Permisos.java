// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package seguridad;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Permisos del usuario de la sesión: qué puede hacer (según el código de su rol)
 * y sobre qué centros (centro principal + centros adicionales vigentes).
 *
 *   ADMINISTRADOR      todo, todos los centros
 *   GERENTE            OC (ver/recibir/anular recepciones), OT completas, inventario, activos con aprobaciones, informes; sus centros
 *   AUXILIAR           igual que gerente, sin finalizar OT ni aprobar/recibir activos; sus centros
 *   ENCARGADO_COMPRAS  proveedores, insumos (crear/modificar), solicitud y OC, OT sin suministrar
 *                      ni finalizar, informes; cualquier centro
 *   CONSULTOR          solo informes; cualquier centro
 *
 * La API lo carga al iniciar sesión y lo vuelve a leer de la base cada 60 s (Sesiones),
 * así un usuario desactivado o con otro rol pierde el acceso igual que en la web.
 */
public final class Permisos implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---- Permisos ----
    public static final String REF_ADMIN = "REF_ADMIN";                 // centros, CC, almacenes, unidades, roles, usuarios
    public static final String PROVEEDORES = "PROVEEDORES";
    public static final String INSUMOS_EDITAR = "INSUMOS_EDITAR";       // ver, crear y modificar insumos
    public static final String INSUMOS_ELIMINAR = "INSUMOS_ELIMINAR";
    public static final String SOLICITUD_COMPRA = "SOLICITUD_COMPRA";
    public static final String OC_VER = "OC_VER";
    public static final String OC_MODIFICAR = "OC_MODIFICAR";           // modificar y anular OC
    public static final String OC_RECEPCION = "OC_RECEPCION";
    public static final String OC_ANULAR_RECEPCION = "OC_ANULAR_RECEPCION"; // anular una recepción ya registrada (con justificación)
    public static final String OT_GESTION = "OT_GESTION";               // crear, ver, modificar, eliminar
    public static final String OT_SUMINISTRAR = "OT_SUMINISTRAR";
    public static final String OT_FINALIZAR = "OT_FINALIZAR";           // finalizar y anular finalización
    public static final String INVENTARIO = "INVENTARIO";
    public static final String ACTIVOS = "ACTIVOS";                     // listar, asignar, reubicar, solicitar salida/baja
    public static final String ACTIVOS_APROBAR = "ACTIVOS_APROBAR";     // aprobar/rechazar salidas y bajas, anular salidas
    public static final String ACTIVOS_RECEPCION = "ACTIVOS_RECEPCION"; // recibir/rechazar y retorno de reparación
    public static final String INFORMES = "INFORMES";

    private final String rol;
    private final boolean todosLosCentros;
    private final HashSet<String> permisos;
    private final LinkedHashSet<Integer> centros;
    private final long cargado = System.currentTimeMillis();
    /**
     * El usuario fue desactivado (o eliminado) después de iniciar sesión. Se guarda "inactivo"
     * y no "activo" para que las sesiones ya guardadas por Tomcat (sin este campo) sigan válidas.
     */
    private boolean usuarioInactivo;

    private Permisos(String rol, boolean todos, Set<String> permisos, Set<Integer> centros) {
        this.rol = rol;
        this.todosLosCentros = todos;
        this.permisos = new HashSet<String>(permisos);
        this.centros = new LinkedHashSet<Integer>(centros);
    }

    private static Set<String> conjunto(String... p) {
        return new HashSet<String>(Arrays.asList(p));
    }

    /** Permisos de cada rol. */
    static Set<String> permisosDelRol(String rol) {
        if ("GERENTE".equals(rol)) {
            return conjunto(OC_VER, OC_RECEPCION, OC_ANULAR_RECEPCION, OT_GESTION, OT_SUMINISTRAR, OT_FINALIZAR, INVENTARIO,
                    ACTIVOS, ACTIVOS_APROBAR, ACTIVOS_RECEPCION, INFORMES);
        }
        if ("AUXILIAR".equals(rol)) {
            return conjunto(OC_VER, OC_RECEPCION, OT_GESTION, OT_SUMINISTRAR, INVENTARIO, ACTIVOS, INFORMES);
        }
        if ("ENCARGADO_COMPRAS".equals(rol)) {
            return conjunto(PROVEEDORES, INSUMOS_EDITAR, SOLICITUD_COMPRA, OC_VER, OC_MODIFICAR, OT_GESTION, INFORMES);
        }
        if ("CONSULTOR".equals(rol)) {
            return conjunto(INFORMES);
        }
        return new HashSet<String>();
    }

    /** Roles que trabajan sobre cualquier centro. */
    static boolean rolDeTodosLosCentros(String rol) {
        return "ADMINISTRADOR".equals(rol) || "ENCARGADO_COMPRAS".equals(rol) || "CONSULTOR".equals(rol);
    }

    public static Permisos cargar(Connection conn, int idUsuario) throws SQLException {
        String rol = null;
        boolean encontrado = false;
        Set<Integer> centros = new LinkedHashSet<Integer>();
        PreparedStatement ps = conn.prepareStatement(
                "SELECT r.codigo, u.centro_asignado FROM usuarios u LEFT JOIN roles r ON r.id_rol = u.id_rol AND r.activo = true "
                + "WHERE u.id_usuario = ? AND u.activo = true");
        try {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                encontrado = true;
                rol = rs.getString(1);
                int c = rs.getInt(2);
                if (!rs.wasNull()) {
                    centros.add(Integer.valueOf(c));
                }
            }
            rs.close();
        } finally {
            ps.close();
        }
        ps = conn.prepareStatement(
                "SELECT id_centro FROM usuario_centros WHERE id_usuario = ? "
                + "AND fecha_desde <= CURRENT_DATE AND (fecha_hasta IS NULL OR fecha_hasta >= CURRENT_DATE)");
        try {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                centros.add(Integer.valueOf(rs.getInt(1)));
            }
            rs.close();
        } finally {
            ps.close();
        }
        Permisos p = crear(rol, centros);
        p.usuarioInactivo = !encontrado;
        return p;
    }

    /** Arma los permisos a partir del código de rol y los centros del usuario. */
    static Permisos crear(String rol, Set<Integer> centros) {
        boolean admin = "ADMINISTRADOR".equals(rol);
        return new Permisos(rol == null ? "" : rol, rolDeTodosLosCentros(rol), admin ? new HashSet<String>() : permisosDelRol(rol), centros);
    }

    /** true si el usuario de la sesión ya no está activo: el FiltroSeguridad cierra su sesión. */
    public boolean usuarioInactivo() {
        return usuarioInactivo;
    }

    public long edadMs() {
        return System.currentTimeMillis() - cargado;
    }

    // ---- Consultas ----
    public String getRol() {
        return rol;
    }

    public boolean esAdministrador() {
        return "ADMINISTRADOR".equals(rol);
    }

    public boolean tiene(String permiso) {
        return esAdministrador() || permisos.contains(permiso);
    }

    /** true si tiene al menos uno de los permisos ("*" = cualquier usuario con sesión). */
    public boolean tieneAlguno(String... lista) {
        for (String p : lista) {
            if ("*".equals(p) || tiene(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean todosLosCentros() {
        return todosLosCentros;
    }

    public Set<Integer> getCentros() {
        return Collections.unmodifiableSet(centros);
    }

    public boolean veCentro(int idCentro) {
        return todosLosCentros || centros.contains(Integer.valueOf(idCentro));
    }

    /** Lanza IllegalArgumentException si el usuario no trabaja con ese centro. */
    public void exigirCentro(int idCentro) {
        if (!veCentro(idCentro)) {
            throw new IllegalArgumentException("No tiene acceso a ese centro. Solo puede operar sobre los centros que tiene asignados.");
        }
    }

    /**
     * Condición SQL para filtrar por los centros del usuario:
     * "TRUE" si ve todos, "columna IN (1,2)" si tiene centros, "FALSE" si no tiene ninguno.
     * Solo contiene números enteros (no hay riesgo de inyección).
     */
    public String sqlCentros(String columna) {
        if (todosLosCentros) {
            return "TRUE";
        }
        if (centros.isEmpty()) {
            return "FALSE";
        }
        StringBuilder sb = new StringBuilder(columna).append(" IN (");
        boolean primero = true;
        for (Integer c : centros) {
            if (!primero) {
                sb.append(',');
            }
            sb.append(c.intValue());
            primero = false;
        }
        return sb.append(')').toString();
    }

    /** Centros como texto "1,2" (vacío si ve todos), para parámetros de reportes. */
    public String centrosCsv() {
        if (todosLosCentros) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer c : centros) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(c.intValue());
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    /** Permisos como objeto JavaScript para las pantallas (solo para mostrar u ocultar botones). */
    public String comoJson() {
        String[] todos = {REF_ADMIN, PROVEEDORES, INSUMOS_EDITAR, INSUMOS_ELIMINAR, SOLICITUD_COMPRA, OC_VER, OC_MODIFICAR,
            OC_RECEPCION, OC_ANULAR_RECEPCION, OT_GESTION, OT_SUMINISTRAR, OT_FINALIZAR, INVENTARIO, ACTIVOS, ACTIVOS_APROBAR, ACTIVOS_RECEPCION, INFORMES};
        StringBuilder sb = new StringBuilder("{\"rol\":\"").append(rol.replaceAll("[^A-Z_]", "")).append('"');
        for (String p : todos) {
            sb.append(",\"").append(p).append("\":").append(tiene(p));
        }
        return sb.append(",\"todosLosCentros\":").append(todosLosCentros).append('}').toString();
    }
}
