package seguridad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;

/**
 * Bitácora del sistema (las mismas tablas bitacora y bitacora_detalle que usa la web):
 * registra quién hizo qué desde la app, cuándo y desde qué IP.
 *
 * Las operaciones que se registran están en OPERACIONES (servicio + tipo). ControladorServlet
 * y PerfilServlet llaman a antes() y despues() alrededor de cada operación: antes() guarda cómo
 * estaba el registro y despues() compara. Solo se registra si la operación cambió algo de verdad.
 * Las descripciones terminan en "(app móvil)" para distinguirlas de las de la web.
 *
 * Un error al escribir la bitácora nunca interrumpe la operación del usuario.
 */
public final class Bitacora {

    private static final Logger LOG = Logger.getLogger(Bitacora.class.getName());
    private static final String ORIGEN = " (app móvil)";

    // Cómo se detecta que la operación se realizó
    private static final int NUEVA = 1;     // apareció una fila nueva
    private static final int CAMBIO = 2;    // cambió la fila
    private static final int BORRADO = 3;   // la fila se eliminó o cambió (baja lógica)
    private static final int GUARDAR = 4;   // alta si no viene "id", modificación si viene

    private Bitacora() {
    }

    /** Tabla auditada: clave primaria, expresión que la describe (alias t) y nombre para mostrar. */
    private static final class Tabla {
        final String nombre;
        final String pk;
        final String descripcion;
        final String etiqueta;

        Tabla(String nombre, String pk, String descripcion, String etiqueta) {
            this.nombre = nombre;
            this.pk = pk;
            this.descripcion = descripcion;
            this.etiqueta = etiqueta;
        }
    }

    /** Operación auditada. */
    private static final class Operacion {
        final String modulo;
        final String accion;
        final Tabla tabla;
        final int deteccion;
        final String parametroId;      // parámetro con el id, o "sesion" (el propio usuario)
        final String condicionUsuario; // filas nuevas: solo las del usuario (con un "?" para su id)

        Operacion(String modulo, String accion, Tabla tabla, int deteccion, String parametroId, String condicionUsuario) {
            this.modulo = modulo;
            this.accion = accion;
            this.tabla = tabla;
            this.deteccion = deteccion;
            this.parametroId = parametroId;
            this.condicionUsuario = condicionUsuario;
        }
    }

    private static final Map<String, Operacion> OPERACIONES = new HashMap<String, Operacion>();

    /** servicio: "otControlador", "movil", "PerfilServlet"... */
    private static void op(String servicio, String tipos, String modulo, String accion, Tabla t, int deteccion,
            String parametroId, String condicionUsuario) {
        for (String tipo : tipos.split(",")) {
            OPERACIONES.put(servicio + "#" + tipo.trim(), new Operacion(modulo, accion, t, deteccion, parametroId, condicionUsuario));
        }
    }

    /** Guardar (alta o modificación según venga "id") y eliminar (con "id") de un referencial de la app. */
    private static void abm(String guardar, String eliminar, String modulo, Tabla t) {
        op("movil", guardar, modulo, "ALTA", t, GUARDAR, "id", null);
        op("movil", eliminar, modulo, "BAJA", t, BORRADO, "id", null);
    }

    static {
        Tabla proveedores = new Tabla("proveedores", "id_proveedor", "t.razon_social", "Proveedor");
        Tabla insumos = new Tabla("insumos", "id_insumo", "t.codigo || ' - ' || t.descripcion", "Insumo");
        Tabla unidades = new Tabla("unidad_medida", "id_unidad_medida", "t.nombre", "Unidad de medida");
        Tabla centros = new Tabla("centros", "id_centro", "t.nombre", "Centro");
        Tabla centrosCosto = new Tabla("centros_costo", "id_centro_costo", "t.codigo", "Centro de costo");
        Tabla almacenes = new Tabla("almacenes", "id_almacen", "t.nombre", "Almacén");
        Tabla roles = new Tabla("roles", "id_rol", "t.nombre", "Rol");
        Tabla usuarios = new Tabla("usuarios", "id_usuario", "t.nombre || ' ' || t.apellido || ' (' || t.email || ')'", "Usuario");
        Tabla accesos = new Tabla("usuario_centros_historial", "id_historial",
                "(SELECT u.email FROM usuarios u WHERE u.id_usuario = t.id_usuario) || ': ' || lower(t.accion) || ' de acceso ' "
                + "|| lower(t.tipo_acceso) || ' a ' || (SELECT c.nombre FROM centros c WHERE c.id_centro = t.id_centro)", "Acceso a centro");
        Tabla compras = new Tabla("compras", "id_compra", "COALESCE(t.numero_oc, 'OC #' || t.id_compra)", "Orden de compra");
        Tabla recepciones = new Tabla("recepciones_oc", "id_recepcion",
                "COALESCE(t.numero, 'Recepción #' || t.id_recepcion) || ' de ' "
                + "|| COALESCE((SELECT c.numero_oc FROM compras c WHERE c.id_compra = t.id_compra), '')", "Recepción de OC");
        Tabla ot = new Tabla("ot", "id_ot", "t.numero_ot || ' - ' || t.titulo", "Orden de trabajo");
        Tabla remisiones = new Tabla("remisiones", "id_remision", "t.numero_remision", "Remisión");
        Tabla ajustes = new Tabla("ajustes", "id_ajuste", "t.numero", "Ajuste");
        Tabla transferencias = new Tabla("transferencias", "id_transferencia", "t.numero", "Transferencia");
        Tabla solicitudes = new Tabla("solicitudes_activos", "id_solicitud", "t.numero", "Solicitud de activos");
        Tabla equipos = new Tabla("equipos", "id_equipo", "t.serial_activo", "Activo");
        Tabla movActivos = new Tabla("movimientos_activos", "id_movimiento",
                "COALESCE((SELECT e.serial_activo FROM equipos e WHERE e.id_equipo = t.id_equipo), '') || ' ' || lower(t.tipo_movimiento)",
                "Movimiento de activo");

        // Referenciales y seguridad (servicio /c/movil)
        abm("guardarProveedor", "eliminarProveedor", "REFERENCIALES", proveedores);
        abm("guardarInsumo", "eliminarInsumo", "REFERENCIALES", insumos);
        abm("guardarUnidad", "eliminarUnidad", "REFERENCIALES", unidades);
        abm("guardarCentro", "eliminarCentro", "REFERENCIALES", centros);
        abm("guardarCentroCosto", "eliminarCentroCosto", "REFERENCIALES", centrosCosto);
        abm("guardarAlmacen", "eliminarAlmacen", "REFERENCIALES", almacenes);
        abm("guardarRol", "eliminarRol", "SEGURIDAD", roles);
        abm("guardarUsuario", "eliminarUsuario", "SEGURIDAD", usuarios);
        op("movil", "agregarCentroUsuario", "SEGURIDAD", "ALTA", accesos, NUEVA, null, "id_usuario_registra = ?");
        op("movil", "quitarCentroUsuario", "SEGURIDAD", "BAJA", accesos, NUEVA, null, "id_usuario_registra = ?");
        op("PerfilServlet", "ActualizarDatos,CambiarPassword,ActualizarFoto", "SEGURIDAD", "MODIFICACION", usuarios, CAMBIO, "sesion", null);

        // Compras
        op("ocControlador", "actualizarOC", "COMPRAS", "MODIFICACION", compras, CAMBIO, "id_compra", null);
        op("ocControlador", "anularOC", "COMPRAS", "ANULACION", compras, CAMBIO, "id", null);
        op("ocControlador", "procesarRecepcion", "COMPRAS", "RECEPCION", recepciones, NUEVA, null, "id_usuario = ?");
        op("ocControlador", "anularRecepcion", "COMPRAS", "ANULACION", recepciones, CAMBIO, "id_recepcion", null);

        // Órdenes de trabajo
        op("otControlador", "crear", "OT", "ALTA", ot, NUEVA, null, "id_usuario_solic = ?");
        op("otControlador", "modificar", "OT", "MODIFICACION", ot, CAMBIO, "id", null);
        op("otControlador", "eliminar", "OT", "BAJA", ot, BORRADO, "id", null);
        op("otControlador", "finalizar", "OT", "FINALIZACION", ot, CAMBIO, "id", null);
        op("otControlador", "reabrir", "OT", "REAPERTURA", ot, CAMBIO, "id", null);

        // Inventario
        op("inventarioControlador", "emitirSalida", "INVENTARIO", "ALTA", remisiones, NUEVA, null, "id_usuario_emite = ?");
        op("inventarioControlador", "anularRemision", "INVENTARIO", "ANULACION", remisiones, CAMBIO, "id", null);
        op("inventarioControlador", "crearAjuste,registrarConteo", "INVENTARIO", "ALTA", ajustes, NUEVA, null, "id_usuario = ?");
        op("inventarioControlador", "anularAjuste", "INVENTARIO", "ANULACION", ajustes, CAMBIO, "id", null);
        op("inventarioControlador", "crearTransferencia", "INVENTARIO", "ALTA", transferencias, NUEVA, null, "id_usuario = ?");
        op("inventarioControlador", "anularTransferencia", "INVENTARIO", "ANULACION", transferencias, CAMBIO, "id", null);

        // Activos fijos
        op("activosMovControlador", "crearSolicitud", "ACTIVOS", "ALTA", solicitudes, NUEVA, null, "id_usuario_solicita = ?");
        op("activosMovControlador", "aprobar", "ACTIVOS", "APROBACION", solicitudes, CAMBIO, "id", null);
        op("activosMovControlador", "rechazar", "ACTIVOS", "RECHAZO", solicitudes, CAMBIO, "id", null);
        op("activosMovControlador", "anularSalida", "ACTIVOS", "ANULACION", solicitudes, CAMBIO, "id", null);
        op("activosMovControlador", "recibir", "ACTIVOS", "RECEPCION", movActivos, NUEVA, null, "id_usuario_registro = ?");
        op("activosMovControlador", "rechazarRecepcion", "ACTIVOS", "RECHAZO", movActivos, NUEVA, null, "id_usuario_registro = ?");
        op("activosControlador", "registrarMovimiento", "ACTIVOS", "MODIFICACION", equipos, CAMBIO, "id_equipo", null);
    }

    // ---------------------------------------------------------------- seguimiento de una operación

    /** Estado guardado antes de la operación. */
    public static final class Seguimiento {
        private final Operacion op;
        private final HttpServletRequest req;
        private final Connection conn;
        private final int idUsuario;
        private final String email;
        private int deteccion;
        private String accion;
        private int id;
        private Map<String, String> antes;
        private String descripcion;
        private int mayorAntes;

        private Seguimiento(Operacion op, HttpServletRequest req, Connection conn, int idUsuario, String email) {
            this.op = op;
            this.req = req;
            this.conn = conn;
            this.idUsuario = idUsuario;
            this.email = email;
        }
    }

    /** Antes de la operación: guarda cómo estaba el registro. Devuelve null si no corresponde. */
    public static Seguimiento antes(HttpServletRequest req, Connection conn, String servicio, String tipo, int idUsuario, String email) {
        Operacion op = tipo == null ? null : OPERACIONES.get(servicio + "#" + tipo);
        if (op == null || conn == null) {
            return null;
        }
        try {
            Seguimiento s = new Seguimiento(op, req, conn, idUsuario, email);
            Tabla t = op.tabla;
            s.deteccion = op.deteccion;
            s.accion = op.accion;
            if (op.deteccion == GUARDAR) {
                int id = idDeLaPeticion(req, op.parametroId, idUsuario);
                s.deteccion = id > 0 ? CAMBIO : NUEVA;
                s.accion = id > 0 ? "MODIFICACION" : "ALTA";
            }
            if (s.deteccion == NUEVA) {
                s.mayorAntes = mayorId(conn, t, op.condicionUsuario, idUsuario);
            } else {
                s.id = idDeLaPeticion(req, op.parametroId, idUsuario);
                if (s.id <= 0) {
                    return null;
                }
                s.antes = foto(conn, t, s.id);
                if (s.antes == null) {
                    return null;
                }
                s.descripcion = describir(conn, t, s.id);
            }
            return s;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bitácora (antes): " + e.getMessage());
            return null;
        }
    }

    /** Después de la operación: si cambió algo, lo registra. */
    public static void despues(Seguimiento s) {
        if (s == null) {
            return;
        }
        try {
            Operacion op = s.op;
            Tabla t = op.tabla;
            if (s.deteccion == NUEVA) {
                int mayor = mayorId(s.conn, t, op.condicionUsuario, s.idUsuario);
                if (mayor <= s.mayorAntes) {
                    return;   // no se creó nada
                }
                int nuevas = nuevasDesde(s.conn, t, s.mayorAntes, op.condicionUsuario, s.idUsuario);
                String desc = texto(t, describir(s.conn, t, mayor)) + (nuevas > 1 ? " (" + nuevas + " registros)" : "");
                guardar(s, t.nombre, Integer.valueOf(mayor), conMotivo(desc, s.req), null);
                return;
            }
            Map<String, String> despues = foto(s.conn, t, s.id);
            if (despues == null) {
                if (s.deteccion == BORRADO) {   // eliminación física
                    guardar(s, t.nombre, Integer.valueOf(s.id), conMotivo(texto(t, s.descripcion), s.req), null);
                }
                return;
            }
            List<String[]> cambios = diferencias(s.conn, t, s.antes, despues);
            if (cambios.isEmpty()) {
                return;   // la operación no se realizó (validación, error o sin cambios)
            }
            guardar(s, t.nombre, Integer.valueOf(s.id), conMotivo(texto(t, s.descripcion), s.req), cambios);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bitácora (después): " + e.getMessage());
        }
    }

    private static void guardar(Seguimiento s, String tabla, Integer idRegistro, String descripcion, List<String[]> cambios) {
        try {
            escribir(s.conn, Integer.valueOf(s.idUsuario), s.email, s.req.getRemoteAddr(), s.op.modulo, s.accion, tabla, idRegistro,
                    descripcion + ORIGEN, cambios);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bitácora: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- registros directos

    /** Inicio y cierre de sesión. Nunca lanza excepciones. */
    public static void sesion(HttpServletRequest req, Connection conn, int idUsuario, String email, String accion, String descripcion) {
        try {
            escribir(conn, Integer.valueOf(idUsuario), email, req.getRemoteAddr(), "SEGURIDAD", accion, "usuarios",
                    Integer.valueOf(idUsuario), descripcion + ORIGEN, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bitácora (sesión): " + e.getMessage());
        }
    }

    /** Inicio de sesión fallido: queda el email que se escribió. Nunca lanza excepciones. */
    public static void inicioFallido(HttpServletRequest req, Connection conn, String email, String motivo) {
        try {
            escribir(conn, null, email, req.getRemoteAddr(), "SEGURIDAD", "LOGIN_FALLIDO", "usuarios", null, motivo + ORIGEN, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bitácora (inicio fallido): " + e.getMessage());
        }
    }

    /**
     * Escribe cabecera y detalle juntos. Si la conexión está dentro de una transacción usa un
     * punto de guardado: un error de la bitácora no deja la transacción del usuario en falla.
     */
    private static void escribir(Connection conn, Integer idUsuario, String email, String ip, String modulo, String accion,
            String tabla, Integer idRegistro, String descripcion, List<String[]> cambios) throws SQLException {
        boolean automatico = conn.getAutoCommit();
        Savepoint punto = null;
        if (automatico) {
            conn.setAutoCommit(false);
        } else {
            punto = conn.setSavepoint();
        }
        try {
            int id;
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO bitacora (id_usuario, usuario_email, ip, modulo, accion, tabla, id_registro, descripcion) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_bitacora");
            try {
                if (idUsuario == null) {
                    ps.setNull(1, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(1, idUsuario.intValue());
                }
                ps.setString(2, corto(email, 150));
                ps.setString(3, corto(ip, 45));
                ps.setString(4, modulo);
                ps.setString(5, accion);
                ps.setString(6, tabla);
                if (idRegistro == null) {
                    ps.setNull(7, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(7, idRegistro.intValue());
                }
                ps.setString(8, corto(descripcion, 300));
                ResultSet rs = ps.executeQuery();
                rs.next();
                id = rs.getInt(1);
                rs.close();
            } finally {
                ps.close();
            }
            if (cambios != null) {
                PreparedStatement pd = conn.prepareStatement(
                        "INSERT INTO bitacora_detalle (id_bitacora, campo, valor_anterior, valor_nuevo) VALUES (?, ?, ?, ?)");
                try {
                    for (String[] c : cambios) {
                        pd.setInt(1, id);
                        pd.setString(2, corto(c[0], 60));
                        pd.setString(3, c[1]);
                        pd.setString(4, c[2]);
                        pd.executeUpdate();
                    }
                } finally {
                    pd.close();
                }
            }
            if (automatico) {
                conn.commit();
            } else {
                conn.releaseSavepoint(punto);
            }
        } catch (SQLException e) {
            if (automatico) {
                conn.rollback();
            } else {
                conn.rollback(punto);
            }
            throw e;
        } finally {
            if (automatico) {
                conn.setAutoCommit(true);
            }
        }
    }

    // ---------------------------------------------------------------- consultas

    private static Map<String, String> foto(Connection conn, Tabla t, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + t.nombre + " WHERE " + t.pk + " = ?");
        try {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData md = rs.getMetaData();
                Map<String, String> m = new LinkedHashMap<String, String>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    m.put(md.getColumnName(i), rs.getString(i));
                }
                return m;
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    private static String describir(Connection conn, Tabla t, int id) throws SQLException {
        return texto(conn, "SELECT " + t.descripcion + " FROM " + t.nombre + " t WHERE t." + t.pk + " = ?", id);
    }

    private static int mayorId(Connection conn, Tabla t, String condicionUsuario, int idUsuario) throws SQLException {
        if (condicionUsuario == null) {
            return entero(conn, "SELECT COALESCE(MAX(" + t.pk + "), 0) FROM " + t.nombre);
        }
        return entero(conn, "SELECT COALESCE(MAX(" + t.pk + "), 0) FROM " + t.nombre + " WHERE " + condicionUsuario, idUsuario);
    }

    private static int nuevasDesde(Connection conn, Tabla t, int desdeId, String condicionUsuario, int idUsuario) throws SQLException {
        if (condicionUsuario == null) {
            return entero(conn, "SELECT COUNT(*) FROM " + t.nombre + " WHERE " + t.pk + " > ?", desdeId);
        }
        return entero(conn, "SELECT COUNT(*) FROM " + t.nombre + " WHERE " + t.pk + " > ? AND " + condicionUsuario, desdeId, idUsuario);
    }

    private static int entero(Connection conn, String sql, int... parametros) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            for (int i = 0; i < parametros.length; i++) {
                ps.setInt(i + 1, parametros[i]);
            }
            ResultSet rs = ps.executeQuery();
            int n = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            return n;
        } finally {
            ps.close();
        }
    }

    private static String texto(Connection conn, String sql, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            String v = rs.next() ? rs.getString(1) : null;
            rs.close();
            return v;
        } finally {
            ps.close();
        }
    }

    // ---------------------------------------------------------------- ayudas

    private static int idDeLaPeticion(HttpServletRequest req, String parametro, int idUsuario) {
        if ("sesion".equals(parametro)) {
            return idUsuario;
        }
        try {
            return Integer.parseInt(req.getParameter(parametro).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String corto(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max - 3) + "...";
    }

    private static String texto(Tabla t, String nombre) {
        return t.etiqueta + (nombre == null || nombre.trim().isEmpty() ? "" : " '" + nombre.trim() + "'");
    }

    private static String conMotivo(String desc, HttpServletRequest req) {
        String m = req.getParameter("motivo");
        return m == null || m.trim().isEmpty() ? desc : desc + " · Motivo: " + m.trim();
    }

    /** Columnas que no se comparan (cambian solas o no aportan). */
    private static final List<String> IGNORADAS = Arrays.asList("notif_vistas_hasta", "id_ultimo_movimiento");
    /** Columnas cuyo valor no se guarda: solo se indica que cambió. */
    private static final List<String> RESERVADAS = Arrays.asList("password", "foto_perfil");

    /** Nombres de campo con tilde o más claros (el resto se muestra como la columna, sin "id_" ni "_"). */
    private static final Map<String, String> ETIQUETAS = new HashMap<String, String>();

    static {
        String[][] e = {{"password", "contraseña"}, {"foto_perfil", "foto de perfil"}, {"razon_social", "razón social"},
            {"telefono", "teléfono"}, {"direccion", "dirección"}, {"descripcion", "descripción"}, {"ubicacion", "ubicación"},
            {"codigo", "código"}, {"centro_asignado", "centro principal"}, {"stock_minimo", "stock mínimo"},
            {"precio_compra", "precio de compra"}, {"observacion", "observación"}, {"observaciones", "observaciones"},
            {"fecha_anulacion", "fecha de anulación"}, {"motivo_anulacion", "motivo de anulación"},
            {"id_usuario_anula", "anulado por"}, {"id_usuario_resuelve", "resuelto por"}, {"fecha_resolucion", "fecha de resolución"},
            {"id_usuario_responsable", "responsable"}, {"motivo_rechazo", "motivo del rechazo"}, {"id_remision", "remisión"},
            {"fecha_finalizacion", "fecha de finalización"}, {"id_usuario_finaliza", "finalizado por"}};
        for (String[] x : e) {
            ETIQUETAS.put(x[0], x[1]);
        }
    }

    /** Nombre legible de los ids de otras tablas (rol, centro, usuario...). */
    private static final String[][] REFERENCIAS = {
        {"^id_rol$", "SELECT nombre FROM roles WHERE id_rol = ?"},
        {"^id_centro_costo.*", "SELECT codigo FROM centros_costo WHERE id_centro_costo = ?"},
        {"^(id_centro|id_centro_destino|id_centro_origen|id_centro_actual|centro_asignado)$", "SELECT nombre FROM centros WHERE id_centro = ?"},
        {"^id_unidad_medida$", "SELECT nombre FROM unidad_medida WHERE id_unidad_medida = ?"},
        {"^id_almacen.*", "SELECT nombre FROM almacenes WHERE id_almacen = ?"},
        {"^id_usuario.*", "SELECT nombre || ' ' || apellido FROM usuarios WHERE id_usuario = ?"},
        {"^id_proveedor$", "SELECT razon_social FROM proveedores WHERE id_proveedor = ?"},
        {"^id_insumo.*", "SELECT descripcion FROM insumos WHERE id_insumo = ?"},
        {"^id_remision$", "SELECT numero_remision FROM remisiones WHERE id_remision = ?"},
        {"^id_ot$", "SELECT numero_ot FROM ot WHERE id_ot = ?"},
        {"^id_compra$", "SELECT numero_oc FROM compras WHERE id_compra = ?"},
        {"^id_equipo$", "SELECT serial_activo FROM equipos WHERE id_equipo = ?"}
    };

    private static List<String[]> diferencias(Connection conn, Tabla t, Map<String, String> antes, Map<String, String> despues) {
        List<String[]> l = new ArrayList<String[]>();
        for (Map.Entry<String, String> e : despues.entrySet()) {
            String col = e.getKey();
            if (col.equals(t.pk) || IGNORADAS.contains(col) || !antes.containsKey(col)) {
                continue;
            }
            String a = antes.get(col);
            String d = e.getValue();
            if (a == null ? d == null : a.equals(d)) {
                continue;
            }
            String campo = ETIQUETAS.containsKey(col) ? ETIQUETAS.get(col)
                    : (col.startsWith("id_") ? col.substring(3) : col).replace('_', ' ');
            if (RESERVADAS.contains(col)) {
                l.add(new String[]{campo, "(oculto)", "(modificado)"});
            } else {
                l.add(new String[]{campo, legible(conn, col, a), legible(conn, col, d)});
            }
        }
        return l;
    }

    private static String legible(Connection conn, String columna, String valor) {
        if (valor == null) {
            return "";
        }
        if ("t".equals(valor)) {
            return "Sí";
        }
        if ("f".equals(valor)) {
            return "No";
        }
        if (valor.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+")) {
            valor = valor.substring(0, valor.indexOf('.'));
        }
        if (valor.matches("\\d+")) {
            for (String[] r : REFERENCIAS) {
                if (columna.matches(r[0])) {
                    try {
                        String n = texto(conn, r[1], Integer.parseInt(valor));
                        if (n != null) {
                            return n + " (" + valor + ")";
                        }
                    } catch (Exception ignorada) {
                        // queda el número
                    }
                    break;
                }
            }
        }
        return valor;
    }
}
