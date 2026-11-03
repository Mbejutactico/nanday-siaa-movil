package portado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Consulta de la bitácora desde la app (solo el Administrador: la operación no figura en
 * ReglasAcceso, así que queda reservada a ese rol). Misma consulta y mismos filtros que la web.
 *
 *   POST /c/bitacoraControlador  tipo=resumen   -> acciones de hoy por tipo
 *                                tipo=usuarios  -> usuarios que tienen registros
 *                                tipo=listar    -> desde, hasta (yyyy-MM-dd), id_usuario, modulo, accion, texto,
 *                                                  tabla e id_registro (historial de un registro)
 *                                tipo=detalle   -> id: datos del registro y campos modificados
 */
public final class BitacoraControlador {

    /** Cantidad máxima de registros por consulta (en el teléfono conviene acotar con los filtros). */
    public static final int LIMITE = 500;

    public static final List<String> MODULOS = Arrays.asList("SEGURIDAD", "REFERENCIALES", "COMPRAS", "OT", "INVENTARIO", "ACTIVOS");
    public static final List<String> ACCIONES = Arrays.asList("LOGIN", "LOGIN_FALLIDO", "LOGOUT", "ALTA", "MODIFICACION", "BAJA",
            "ANULACION", "APROBACION", "RECHAZO", "FINALIZACION", "REAPERTURA", "RECEPCION");

    private static final String COLUMNAS = "SELECT b.id_bitacora, b.fecha, TRIM(COALESCE(u.nombre, '') || ' ' || COALESCE(u.apellido, '')) AS usuario, "
            + "b.usuario_email, b.ip, b.modulo, b.accion, b.tabla, b.id_registro, b.descripcion, "
            + "(SELECT COUNT(*) FROM bitacora_detalle d WHERE d.id_bitacora = b.id_bitacora) AS cambios "
            + "FROM bitacora b LEFT JOIN usuarios u ON u.id_usuario = b.id_usuario ";

    private BitacoraControlador() {
    }

    public static Object atender(String tipo, HttpServletRequest req, Connection conn) throws SQLException {
        if (!NotificacionControlador.existeTabla(conn, "bitacora")) {
            throw new IllegalArgumentException("La bitácora todavía no está creada en la base de datos: ejecute el script bitacora.sql.");
        }
        if ("resumen".equals(tipo)) {
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*), "
                    + "COUNT(*) FILTER (WHERE accion = 'LOGIN'), "
                    + "COUNT(*) FILTER (WHERE accion = 'LOGIN_FALLIDO'), "
                    + "COUNT(*) FILTER (WHERE accion IN ('ALTA', 'MODIFICACION')), "
                    + "COUNT(*) FILTER (WHERE accion IN ('BAJA', 'ANULACION')) "
                    + "FROM bitacora WHERE fecha >= CURRENT_DATE");
            try {
                ResultSet rs = ps.executeQuery();
                rs.next();
                JSONObject r = new JSONObject().put("TOTAL", rs.getInt(1)).put("LOGIN", rs.getInt(2)).put("LOGIN_FALLIDO", rs.getInt(3))
                        .put("CAMBIOS", rs.getInt(4)).put("BAJAS", rs.getInt(5));
                rs.close();
                return r;
            } finally {
                ps.close();
            }
        }
        if ("usuarios".equals(tipo)) {
            PreparedStatement ps = conn.prepareStatement("SELECT u.id_usuario, u.nombre || ' ' || u.apellido || ' (' || u.email || ')' FROM usuarios u "
                    + "WHERE EXISTS (SELECT 1 FROM bitacora b WHERE b.id_usuario = u.id_usuario) ORDER BY u.nombre, u.apellido");
            try {
                ResultSet rs = ps.executeQuery();
                JSONArray a = new JSONArray();
                while (rs.next()) {
                    a.put(new JSONObject().put("id", rs.getInt(1)).put("nombre", rs.getString(2)));
                }
                rs.close();
                return a;
            } finally {
                ps.close();
            }
        }
        if ("listar".equals(tipo)) {
            return listar(req, conn);
        }
        if ("detalle".equals(tipo)) {
            return detalle(conn, numero(req.getParameter("id")));
        }
        return new JSONObject().put("ok", false).put("error", "Operación no reconocida");
    }

    private static JSONObject listar(HttpServletRequest req, Connection conn) throws SQLException {
        String desde = fecha(req.getParameter("desde"), "La fecha desde no es válida.");
        String hasta = fecha(req.getParameter("hasta"), "La fecha hasta no es válida.");
        if (!desde.isEmpty() && !hasta.isEmpty() && desde.compareTo(hasta) > 0) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        int idUsuario = Math.max(0, numero(req.getParameter("id_usuario")));
        String modulo = MODULOS.contains(req.getParameter("modulo")) ? req.getParameter("modulo") : "";
        String accion = ACCIONES.contains(req.getParameter("accion")) ? req.getParameter("accion") : "";
        String texto = req.getParameter("texto") == null ? "" : req.getParameter("texto").trim();
        String tabla = req.getParameter("tabla") == null || !req.getParameter("tabla").matches("[a-z_]{1,60}") ? "" : req.getParameter("tabla");
        int idRegistro = Math.max(0, numero(req.getParameter("id_registro")));

        StringBuilder sql = new StringBuilder(COLUMNAS).append("WHERE TRUE ");
        List<Object> p = new ArrayList<Object>();
        if (!desde.isEmpty()) {
            sql.append("AND b.fecha >= CAST(? AS date) ");
            p.add(desde);
        }
        if (!hasta.isEmpty()) {
            sql.append("AND b.fecha < CAST(? AS date) + 1 ");
            p.add(hasta);
        }
        if (idUsuario > 0) {
            sql.append("AND b.id_usuario = ? ");
            p.add(idUsuario);
        }
        if (!modulo.isEmpty()) {
            sql.append("AND b.modulo = ? ");
            p.add(modulo);
        }
        if (!accion.isEmpty()) {
            sql.append("AND b.accion = ? ");
            p.add(accion);
        }
        if (!texto.isEmpty()) {
            sql.append("AND (b.descripcion ILIKE ? OR b.usuario_email ILIKE ? OR b.ip ILIKE ? "
                    + "OR EXISTS (SELECT 1 FROM bitacora_detalle d WHERE d.id_bitacora = b.id_bitacora "
                    + "AND (d.valor_anterior ILIKE ? OR d.valor_nuevo ILIKE ?))) ");
            String t = "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            for (int i = 0; i < 5; i++) {
                p.add(t);
            }
        }
        if (!tabla.isEmpty() && idRegistro > 0) {
            sql.append("AND b.tabla = ? AND b.id_registro = ? ");
            p.add(tabla);
            p.add(idRegistro);
        }
        sql.append("ORDER BY b.fecha DESC, b.id_bitacora DESC LIMIT ").append(LIMITE);

        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        JSONArray a = new JSONArray();
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        try {
            for (int i = 0; i < p.size(); i++) {
                Object v = p.get(i);
                if (v instanceof Integer) {
                    ps.setInt(i + 1, ((Integer) v).intValue());
                } else {
                    ps.setString(i + 1, (String) v);
                }
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                a.put(new JSONObject()
                        .put("id", rs.getInt("id_bitacora"))
                        .put("fecha", f.format(rs.getTimestamp("fecha")))
                        .put("usuario", nombre(rs.getString("usuario"), rs.getString("usuario_email")))
                        .put("ip", txt(rs.getString("ip")))
                        .put("modulo", txt(rs.getString("modulo")))
                        .put("accion", txt(rs.getString("accion")))
                        .put("descripcion", txt(rs.getString("descripcion")))
                        .put("cambios", rs.getInt("cambios")));
            }
            rs.close();
        } finally {
            ps.close();
        }
        return new JSONObject().put("registros", a).put("limite", LIMITE);
    }

    private static JSONObject detalle(Connection conn, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(COLUMNAS + "WHERE b.id_bitacora = ?");
        JSONObject r = new JSONObject();
        try {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                rs.close();
                throw new IllegalArgumentException("El registro no existe.");
            }
            int idReg = rs.getInt("id_registro");
            boolean sinRegistro = rs.wasNull();
            r.put("id", rs.getInt("id_bitacora"))
                    .put("fecha", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(rs.getTimestamp("fecha")))
                    .put("usuario", nombre(rs.getString("usuario"), rs.getString("usuario_email")))
                    .put("email", txt(rs.getString("usuario_email")))
                    .put("ip", txt(rs.getString("ip")))
                    .put("modulo", txt(rs.getString("modulo")))
                    .put("accion", txt(rs.getString("accion")))
                    .put("tabla", txt(rs.getString("tabla")))
                    .put("id_registro", sinRegistro ? 0 : idReg)
                    .put("descripcion", txt(rs.getString("descripcion")));
            rs.close();
        } finally {
            ps.close();
        }
        JSONArray c = new JSONArray();
        PreparedStatement pd = conn.prepareStatement(
                "SELECT campo, valor_anterior, valor_nuevo FROM bitacora_detalle WHERE id_bitacora = ? ORDER BY id_detalle");
        try {
            pd.setInt(1, id);
            ResultSet rs = pd.executeQuery();
            while (rs.next()) {
                c.put(new JSONObject().put("campo", txt(rs.getString(1))).put("anterior", txt(rs.getString(2))).put("nuevo", txt(rs.getString(3))));
            }
            rs.close();
        } finally {
            pd.close();
        }
        return r.put("cambios", c);
    }

    private static String nombre(String usuario, String email) {
        String n = usuario == null ? "" : usuario.trim();
        return n.isEmpty() ? txt(email) : n;
    }

    private static String fecha(String v, String error) {
        if (v == null || v.trim().isEmpty()) {
            return "";
        }
        if (!v.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException(error);
        }
        return v.trim();
    }

    private static String txt(String v) {
        return v == null ? "" : v;
    }

    private static int numero(String v) {
        try {
            return Integer.parseInt(v == null ? "" : v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
