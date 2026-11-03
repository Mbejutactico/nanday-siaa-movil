package portado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import seguridad.Permisos;

/**
 * Centro de notificaciones de la app (la campana), con las mismas reglas que la web
 * (NotificacionServicio y NotificacionDAOJdbc del sistema web).
 *
 *   POST /c/notificacionControlador  tipo=listar        -> {pendientes:[...], novedades:[...], nuevas:N}
 *                                    tipo=marcarVistas  -> {ok:true}
 *
 * Pendientes (siguen hasta que se resuelven), según el permiso del rol:
 *   Stock en o bajo el mínimo      INVENTARIO o SOLICITUD_COMPRA
 *   Solicitudes por aprobar        ACTIVOS_APROBAR
 *   Activos por recibir            ACTIVOS_RECEPCION
 *   Inicios de sesión fallidos     solo ADMINISTRADOR
 * Novedades de los últimos 7 días: OT nuevas o asignadas (permisos de OT) y resultado de las
 * solicitudes de activos propias (ACTIVOS). Lo visto se guarda en usuarios.notif_vistas_hasta,
 * el mismo campo que usa la web: lo que se ve en un lado queda visto en el otro.
 */
public final class NotificacionControlador {

    private NotificacionControlador() {
    }

    public static JSONObject atender(String tipo, HttpServletRequest req, Connection conn, Permisos p, int idUsuario) throws SQLException {
        JSONObject r = new JSONObject();
        if ("listar".equals(tipo)) {
            List<JSONObject> novedades = novedades(conn, p, idUsuario);
            int nuevas = 0;
            for (JSONObject n : novedades) {
                if (n.optBoolean("nueva")) {
                    nuevas++;
                }
            }
            r.put("pendientes", new JSONArray(pendientes(conn, p)));
            r.put("novedades", new JSONArray(novedades));
            r.put("nuevas", nuevas);
        } else if ("marcarVistas".equals(tipo)) {
            PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET notif_vistas_hasta = CURRENT_TIMESTAMP WHERE id_usuario = ?");
            try {
                ps.setInt(1, idUsuario);
                ps.executeUpdate();
            } finally {
                ps.close();
            }
            r.put("ok", true);
        } else {
            r.put("ok", false).put("error", "Operación no reconocida");
        }
        return r;
    }

    public static boolean veStock(Permisos p) {
        return p.tieneAlguno(Permisos.INVENTARIO, Permisos.SOLICITUD_COMPRA);
    }

    private static boolean veOt(Permisos p) {
        return p.tieneAlguno(Permisos.OT_GESTION, Permisos.OT_SUMINISTRAR, Permisos.OT_FINALIZAR);
    }

    // ------------------------------------------------------------------ pendientes

    private static List<JSONObject> pendientes(Connection conn, Permisos p) throws SQLException {
        List<JSONObject> l = new ArrayList<JSONObject>();
        if (veStock(p)) {
            int n = entero(conn, "SELECT COUNT(*) FROM stock_almacen s JOIN insumos i ON i.id_insumo = s.id_insumo "
                    + "JOIN almacenes a ON a.id_almacen = s.id_almacen "
                    + "WHERE s.stock_minimo > 0 AND s.stock_actual <= s.stock_minimo AND i.activo = true AND " + p.sqlCentros("a.id_centro"));
            agregar(l, "STOCK", n, n == 1 ? "1 insumo en o bajo el stock mínimo" : n + " insumos en o bajo el stock mínimo");
        }
        if (p.tiene(Permisos.ACTIVOS_APROBAR)) {
            // Solo las que puede resolver: todos los activos salen de sus centros (misma regla que al aprobar)
            String cond = p.todosLosCentros() ? "TRUE"
                    : "EXISTS (SELECT 1 FROM solicitudes_activos_detalle d WHERE d.id_solicitud = s.id_solicitud AND " + p.sqlCentros("d.id_centro_origen") + ") "
                    + "AND NOT EXISTS (SELECT 1 FROM solicitudes_activos_detalle d WHERE d.id_solicitud = s.id_solicitud "
                    + "AND NOT (" + p.sqlCentros("COALESCE(d.id_centro_origen, 0)") + "))";
            int n = entero(conn, "SELECT COUNT(*) FROM solicitudes_activos s WHERE s.estado = 'PENDIENTE' AND " + cond);
            agregar(l, "APROBAR", n, n == 1 ? "1 solicitud de activos por aprobar" : n + " solicitudes de activos por aprobar");
        }
        if (p.tiene(Permisos.ACTIVOS_RECEPCION)) {
            String cond = p.todosLosCentros() ? "TRUE"
                    : "((s.tipo = 'REPARACION' AND " + p.sqlCentros("d.id_centro_origen") + ") OR (s.tipo <> 'REPARACION' AND "
                    + p.sqlCentros("s.id_centro_destino") + "))";
            int n = entero(conn, "SELECT COUNT(*) FROM solicitudes_activos_detalle d JOIN solicitudes_activos s ON s.id_solicitud = d.id_solicitud "
                    + "WHERE d.estado_linea = 'EN_CURSO' AND s.tipo <> 'BAJA' AND " + cond);
            agregar(l, "RECIBIR", n, n == 1 ? "1 activo por recibir" : n + " activos por recibir");
        }
        if (p.esAdministrador() && existeTabla(conn, "bitacora")) {
            int n = entero(conn, "SELECT COUNT(*) FROM bitacora WHERE accion = 'LOGIN_FALLIDO' AND fecha >= CURRENT_DATE");
            agregar(l, "SEGURIDAD", n, n == 1 ? "1 inicio de sesión fallido hoy" : n + " inicios de sesión fallidos hoy");
        }
        return l;
    }

    private static void agregar(List<JSONObject> l, String tipo, int cantidad, String texto) {
        if (cantidad <= 0) {
            return;
        }
        l.add(new JSONObject().put("clase", "PENDIENTE").put("tipo", tipo).put("cantidad", cantidad).put("texto", texto)
                .put("detalle", "").put("fecha", "").put("nueva", false).put("id", 0));
    }

    // ------------------------------------------------------------------ novedades

    private static List<JSONObject> novedades(Connection conn, Permisos p, int idUsuario) throws SQLException {
        List<Object[]> l = new ArrayList<Object[]>();   // {fecha, JSONObject}
        if (veOt(p)) {
            otAsignadas(conn, p, idUsuario, l);
        }
        if (p.tiene(Permisos.ACTIVOS)) {
            solicitudesResueltas(conn, idUsuario, l);
        }
        Collections.sort(l, (a, b) -> ((Timestamp) b[0]).compareTo((Timestamp) a[0]));
        Timestamp vistas = vistasHasta(conn, idUsuario);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        List<JSONObject> r = new ArrayList<JSONObject>();
        for (Object[] x : l) {
            if (r.size() >= 10) {
                break;
            }
            Timestamp f = (Timestamp) x[0];
            JSONObject n = (JSONObject) x[1];
            n.put("fecha", iso.format(f));
            n.put("nueva", vistas == null || f.after(vistas));
            r.add(n);
        }
        return r;
    }

    /** Centros propios del usuario (principal y adicionales vigentes), aunque su rol vea todos. */
    private static String misCentros(Permisos p, String columna) {
        StringBuilder sb = new StringBuilder();
        for (Integer c : p.getCentros()) {
            sb.append(sb.length() == 0 ? "" : ",").append(c.intValue());
        }
        return sb.length() == 0 ? "FALSE" : columna + " IN (" + sb + ")";
    }

    private static void otAsignadas(Connection conn, Permisos p, int idUsuario, List<Object[]> l) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM ("
                + "  SELECT DISTINCT ON (o.id_ot) o.id_ot, o.numero_ot, o.titulo, c.nombre AS centro, e.tipo, e.fecha, e.id_evento "
                + "  FROM ot_eventos e JOIN ot o ON o.id_ot = e.id_ot LEFT JOIN centros c ON c.id_centro = o.id_centro "
                + "  WHERE e.fecha >= CURRENT_TIMESTAMP - INTERVAL '7 days' "
                + "    AND (e.id_usuario IS NULL OR e.id_usuario <> ?) "
                + "    AND o.estado IN ('PENDIENTE', 'EN_PROCESO') "
                + "    AND ((e.tipo IN ('CREACION', 'CAMBIO_CENTRO') AND " + misCentros(p, "o.id_centro") + ") "
                + "         OR (e.tipo = 'RESPONSABLE' AND o.id_usuario_responsable = ?)) "
                + "  ORDER BY o.id_ot, e.fecha DESC, e.id_evento DESC"
                + ") x ORDER BY x.fecha DESC LIMIT 10");
        try {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idUsuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String numero = rs.getString("numero_ot");
                String evento = rs.getString("tipo");
                String texto = "RESPONSABLE".equals(evento) ? "Usted es responsable de la " + numero
                        : "CAMBIO_CENTRO".equals(evento) ? numero + " pasó a su centro" : "Nueva " + numero + " para su centro";
                String titulo = rs.getString("titulo");
                String centro = rs.getString("centro");
                JSONObject n = new JSONObject().put("clase", "NOVEDAD").put("tipo", "OT").put("cantidad", 0)
                        .put("texto", texto).put("detalle", (titulo == null ? "" : titulo) + (centro == null ? "" : " · " + centro))
                        .put("id", rs.getInt("id_ot"));
                l.add(new Object[]{rs.getTimestamp("fecha"), n});
            }
            rs.close();
        } finally {
            ps.close();
        }
    }

    private static void solicitudesResueltas(Connection conn, int idUsuario, List<Object[]> l) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT s.id_solicitud, s.numero, s.tipo, s.estado, s.fecha_resolucion, s.motivo_rechazo, r.numero_remision "
                + "FROM solicitudes_activos s LEFT JOIN remisiones r ON r.id_remision = s.id_remision "
                + "WHERE s.id_usuario_solicita = ? AND s.estado IN ('APROBADA', 'RECHAZADA') "
                + "  AND s.fecha_resolucion >= CURRENT_TIMESTAMP - INTERVAL '7 days' "
                + "  AND (s.id_usuario_resuelve IS NULL OR s.id_usuario_resuelve <> ?) "
                + "ORDER BY s.fecha_resolucion DESC LIMIT 10");
        try {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idUsuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                boolean aprobada = "APROBADA".equals(rs.getString("estado"));
                String detalle;
                if (!aprobada) {
                    String motivo = rs.getString("motivo_rechazo");
                    detalle = motivo == null ? "" : "Motivo: " + motivo;
                } else if ("BAJA".equals(rs.getString("tipo"))) {
                    detalle = "Baja aprobada";
                } else {
                    String rem = rs.getString("numero_remision");
                    detalle = rem == null ? "" : "Remisión " + rem;
                }
                JSONObject n = new JSONObject().put("clase", "NOVEDAD").put("tipo", "SOLICITUD").put("cantidad", 0)
                        .put("texto", "Su solicitud " + rs.getString("numero") + (aprobada ? " fue aprobada" : " fue rechazada"))
                        .put("detalle", detalle).put("id", rs.getInt("id_solicitud"));
                l.add(new Object[]{rs.getTimestamp("fecha_resolucion"), n});
            }
            rs.close();
        } finally {
            ps.close();
        }
    }

    private static Timestamp vistasHasta(Connection conn, int idUsuario) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT notif_vistas_hasta FROM usuarios WHERE id_usuario = ?");
        try {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            Timestamp t = rs.next() ? rs.getTimestamp(1) : null;
            rs.close();
            return t;
        } finally {
            ps.close();
        }
    }

    // ------------------------------------------------------------------ ayudas

    static int entero(Connection conn, String sql) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            ResultSet rs = ps.executeQuery();
            int n = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            return n;
        } finally {
            ps.close();
        }
    }

    static boolean existeTabla(Connection conn, String tabla) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?");
        try {
            ps.setString(1, tabla);
            ResultSet rs = ps.executeQuery();
            boolean e = rs.next();
            rs.close();
            return e;
        } finally {
            ps.close();
        }
    }
}
