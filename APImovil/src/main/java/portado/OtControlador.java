package portado;

import inventario.MovimientoStock;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import seguridad.Permisos;
import seguridad.SesionDatos;
import util.Errores;
import util.Html;

/**
 * Copia de la web: Controlador/otControlador.jsp
 * La lógica (validaciones, transacciones, stock y Kardex) es la misma que usa el sistema web.
 * Generado por portar.py; solo se quitaron las operaciones de impresión en PDF.
 */
@SuppressWarnings({"unused", "rawtypes"})
public final class OtControlador {

    private OtControlador() {
    }

    public static void atender(HttpServletRequest request, HttpServletResponse response, PrintWriter out, Connection conn,
                               Permisos permisos, SesionDatos session, ServletContext application) throws Exception {

    String tipo = request.getParameter("tipo");
    PreparedStatement ps = null;
    ResultSet rs = null;
    int idUsuario = ((Integer) session.getAttribute("id_usuario")).intValue();

    try {
        // ---------- DATOS DEL USUARIO (pestaña "Datos del usuario") ----------
        if ("contextoUsuario".equals(tipo)) {
            JSONObject u = new JSONObject();
            ps = conn.prepareStatement(
                "SELECT u.id_usuario, u.nombre, u.apellido, u.email, r.nombre AS rol, u.centro_asignado, c.nombre AS centro " +
                "FROM usuarios u LEFT JOIN roles r ON r.id_rol = u.id_rol " +
                "LEFT JOIN centros c ON c.id_centro = u.centro_asignado WHERE u.id_usuario = ?");
            ps.setInt(1, idUsuario);
            rs = ps.executeQuery();
            if (rs.next()) {
                u.put("id_usuario", rs.getInt("id_usuario"));
                u.put("nombre", txt(rs.getString("nombre")) + " " + txt(rs.getString("apellido")));
                u.put("email", txt(rs.getString("email")));
                u.put("rol", txt(rs.getString("rol")));
                int idCentro = rs.getInt("centro_asignado");
                u.put("id_centro", rs.wasNull() ? 0 : idCentro);
                u.put("centro", txt(rs.getString("centro")));
                u.put("fecha", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()));
                rs.close(); ps.close(); rs = null; ps = null;
                // Centros destino posibles: todos los activos (la OT se pide al centro que la resuelve)
                JSONArray centrosOT = new JSONArray();
                ps = conn.prepareStatement("SELECT id_centro, nombre FROM centros WHERE activo = true ORDER BY nombre");
                rs = ps.executeQuery();
                while (rs.next()) {
                    JSONObject c = new JSONObject();
                    c.put("id", rs.getInt(1));
                    c.put("nombre", txt(rs.getString(2)));
                    centrosOT.put(c);
                }
                u.put("centros", centrosOT);
            }
            responderJson(response, out, u);
        }

        // ---------- FECHA Y HORA ACTUAL DEL SERVIDOR (se muestra al finalizar / anular) ----------
        else if ("ahora".equals(tipo)) {
            JSONObject a = new JSONObject();
            a.put("fecha", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()));
            responderJson(response, out, a);
        }

        // ---------- CENTROS DE COSTO DE UN CENTRO ----------
        else if ("listarCentrosCosto".equals(tipo)) {
            // Cualquier centro activo: la OT puede ir dirigida a otro centro
            int idCentro = numero(request.getParameter("id_centro"));
            ps = conn.prepareStatement("SELECT id_centro_costo, codigo, descripcion FROM centros_costo WHERE id_centro = ? AND activo = true ORDER BY codigo");
            ps.setInt(1, idCentro);
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt("id_centro_costo") + "'>" + Html.esc(nombreCC(rs.getString("codigo"), rs.getString("descripcion"))) + "</option>");
            }
        }

        // ---------- RESPONSABLES POSIBLES ----------
        // id_centro > 0: usuarios que trabajan en ese centro (principal o adicional vigente).
        // id_centro = 0: todos, con su centro principal (al elegirlo se completa el centro destino).
        else if ("listarResponsables".equals(tipo)) {
            int idCentro = numero(request.getParameter("id_centro"));
            ps = conn.prepareStatement(
                "SELECT u.id_usuario, u.nombre || ' ' || u.apellido AS nombre, r.nombre AS rol, u.centro_asignado, c.nombre AS centro " +
                "FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol AND r.activo = true " +
                "LEFT JOIN centros c ON c.id_centro = u.centro_asignado " +
                "WHERE " + SQL_RESPONSABLE_POSIBLE + " AND (? = 0 OR u.centro_asignado = ? OR EXISTS (SELECT 1 FROM usuario_centros uc " +
                "      WHERE uc.id_usuario = u.id_usuario AND uc.id_centro = ? AND uc.fecha_desde <= CURRENT_DATE " +
                "        AND (uc.fecha_hasta IS NULL OR uc.fecha_hasta >= CURRENT_DATE))) " +
                "ORDER BY u.apellido, u.nombre");
            ps.setInt(1, idCentro);
            ps.setInt(2, idCentro);
            ps.setInt(3, idCentro);
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject u = new JSONObject();
                u.put("id", rs.getInt("id_usuario"));
                u.put("nombre", txt(rs.getString("nombre")));
                u.put("rol", txt(rs.getString("rol")));
                int c = rs.getInt("centro_asignado");
                u.put("id_centro", rs.wasNull() ? 0 : c);
                u.put("centro", txt(rs.getString("centro")));
                lista.put(u);
            }
            responderJson(response, out, lista);
        }

        // ---------- CREAR OT ----------
        else if ("crear".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                String titulo = limpio(request.getParameter("titulo"));
                String descripcion = limpio(request.getParameter("descripcion"));
                int idCC = numero(request.getParameter("id_centro_costo"));
                if (titulo.isEmpty()) {
                    throw new IllegalArgumentException("Ingrese un título para la orden de trabajo");
                }
                if (titulo.length() > 200) {
                    throw new IllegalArgumentException("El título no puede superar 200 caracteres");
                }
                if (descripcion.length() > 2000) {
                    throw new IllegalArgumentException("La descripción no puede superar 2000 caracteres");
                }
                if (idCC == 0) {
                    throw new IllegalArgumentException("Seleccione el centro de costo");
                }
                // Centro destino: obligatorio, cualquier centro activo
                int idCentro = numero(request.getParameter("id_centro"));
                if (idCentro == 0 || !existe(conn, "SELECT 1 FROM centros WHERE id_centro = ? AND activo = true", idCentro, 0)) {
                    throw new IllegalArgumentException("Seleccione el centro destino (el centro que va a resolver la orden de trabajo)");
                }
                if (!existe(conn, "SELECT 1 FROM centros_costo WHERE id_centro_costo = ? AND id_centro = ? AND activo = true", idCC, idCentro)) {
                    throw new IllegalArgumentException("El centro de costo no pertenece al centro destino");
                }
                // Responsable: opcional, debe trabajar en el centro destino
                int idResponsable = numero(request.getParameter("id_responsable"));
                if (idResponsable != 0 && !responsableValido(conn, idResponsable, idCentro)) {
                    throw new IllegalArgumentException("El responsable elegido no trabaja en el centro destino (o no está activo)");
                }

                // Número OT-AAAAMMDD-0001 (correlativo diario atómico)
                ps = conn.prepareStatement(
                    "INSERT INTO correlativo_ot (fecha, ultimo) VALUES (CURRENT_DATE, 1) " +
                    "ON CONFLICT (fecha) DO UPDATE SET ultimo = correlativo_ot.ultimo + 1 RETURNING ultimo, fecha");
                rs = ps.executeQuery();
                rs.next();
                String numeroOT = "OT-" + new SimpleDateFormat("yyyyMMdd").format(rs.getDate("fecha")) + "-" + String.format("%04d", rs.getInt("ultimo"));
                rs.close(); ps.close(); rs = null; ps = null;

                ps = conn.prepareStatement(
                    "INSERT INTO ot (numero_ot, fecha_solicitud, fecha_creacion, id_centro, id_centro_costo, titulo, descripcion, estado, id_usuario_solic, id_usuario_responsable) " +
                    "VALUES (?, CURRENT_DATE, CURRENT_TIMESTAMP, ?, ?, ?, ?, 'PENDIENTE', ?, ?) RETURNING id_ot, fecha_creacion");
                ps.setString(1, numeroOT);
                ps.setInt(2, idCentro);
                ps.setInt(3, idCC);
                ps.setString(4, titulo);
                if (descripcion.isEmpty()) { ps.setNull(5, Types.VARCHAR); } else { ps.setString(5, descripcion); }
                ps.setInt(6, idUsuario);
                if (idResponsable == 0) { ps.setNull(7, Types.INTEGER); } else { ps.setInt(7, idResponsable); }
                rs = ps.executeQuery();
                rs.next();
                int idOT = rs.getInt(1);
                String fechaCreacion = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(rs.getTimestamp(2));
                rs.close(); ps.close(); rs = null; ps = null;

                evento(conn, idOT, "CREACION", idUsuario, "OT creada para el centro " + nombreDe(conn, "centros", "id_centro", idCentro)
                        + ": " + titulo + (idResponsable == 0 ? "" : ". Responsable: " + nombreUsuario(conn, idResponsable)));
                conn.commit();
                resp.put("ok", true);
                resp.put("id_ot", idOT);
                resp.put("numero_ot", numeroOT);
                resp.put("fecha", fechaCreacion);
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } catch (Exception e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", "Error al crear la OT: " + Errores.mensaje(application, e));
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // ---------- RESUMEN (tarjetas) ----------
        else if ("resumen".equals(tipo)) {
            JSONObject r = new JSONObject();
            r.put("PENDIENTE", 0);
            r.put("EN_PROCESO", 0);
            r.put("FINALIZADA", 0);
            ps = conn.prepareStatement("SELECT estado, COUNT(*) FROM ot o WHERE " + condVisible(permisos, idUsuario) + " GROUP BY estado");
            rs = ps.executeQuery();
            int total = 0;
            while (rs.next()) {
                r.put(rs.getString(1), rs.getInt(2));
                total += rs.getInt(2);
            }
            r.put("TOTAL", total);
            responderJson(response, out, r);
        }

        // ---------- LISTAR OT (JSON) ----------
        // estado: '' (todos) | PENDIENTE | EN_PROCESO | FINALIZADA | ABIERTAS (PENDIENTE + EN_PROCESO)
        else if ("listar".equals(tipo)) {
            String estado = request.getParameter("estado");
            if (!"ABIERTAS".equals(estado) && !"PENDIENTE".equals(estado) && !"EN_PROCESO".equals(estado)
                    && !"FINALIZADA".equals(estado) && !"ANULADA".equals(estado)) {
                estado = "";
            }
            // mias: '' (todas las que puede ver) | SOLICITADAS (las que pidió) | A_CARGO (es el responsable)
            String mias = request.getParameter("mias");
            String condMias = "SOLICITADAS".equals(mias) ? " AND o.id_usuario_solic = " + idUsuario
                    : "A_CARGO".equals(mias) ? " AND o.id_usuario_responsable = " + idUsuario : "";
            ps = conn.prepareStatement(
                "SELECT o.id_ot, o.numero_ot, o.fecha_solicitud, o.fecha_creacion, o.titulo, o.estado, o.fecha_finalizacion, o.fecha_anulacion_fin, " +
                "       o.id_centro, c.nombre AS centro, cc.codigo AS cc_codigo, cc.descripcion AS cc_descripcion, " +
                "       u.nombre || ' ' || u.apellido AS solicitante, uc.nombre AS centro_solicitante, " +
                "       ur.nombre || ' ' || ur.apellido AS responsable, o.id_usuario_responsable, o.id_usuario_solic, o.id_usuario_finaliza, " +
                "       (SELECT COUNT(*) FROM detalle_ot d WHERE d.id_ot = o.id_ot) AS items, " +
                "       (SELECT COALESCE(SUM(d.cantidad), 0) FROM detalle_ot d WHERE d.id_ot = o.id_ot) AS unidades, " +
                "       (SELECT COUNT(*) FROM detalle_ot d LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false " +
                "         WHERE d.id_ot = o.id_ot AND r.id_remision IS NULL) AS sin_salida, " +
                "       (SELECT COUNT(*) FROM remisiones r WHERE r.id_ot = o.id_ot AND r.anulada = false " +
                "         AND r.tipo_remision IN ('SALIDA_INSUMOS', 'SALIDA_ACTIVOS')) AS salidas " +
                "FROM ot o " +
                "LEFT JOIN centros c ON c.id_centro = o.id_centro " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = o.id_centro_costo " +
                "LEFT JOIN usuarios u ON u.id_usuario = o.id_usuario_solic " +
                "LEFT JOIN centros uc ON uc.id_centro = u.centro_asignado " +
                "LEFT JOIN usuarios ur ON ur.id_usuario = o.id_usuario_responsable " +
                "WHERE (? = '' OR (? = 'ABIERTAS' AND o.estado IN ('PENDIENTE', 'EN_PROCESO')) OR o.estado = ?) AND " + condVisible(permisos, idUsuario) + condMias + " " +
                "ORDER BY o.fecha_solicitud DESC, o.id_ot DESC");
            ps.setString(1, estado);
            ps.setString(2, estado);
            ps.setString(3, estado);
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_ot"));
                o.put("numero", txt(rs.getString("numero_ot")));
                o.put("fecha", rs.getDate("fecha_solicitud") == null ? "" : rs.getDate("fecha_solicitud").toString());
                o.put("titulo", txt(rs.getString("titulo")));
                o.put("estado", txt(rs.getString("estado")));
                o.put("centro", txt(rs.getString("centro")));
                o.put("centro_costo", nombreCC(rs.getString("cc_codigo"), rs.getString("cc_descripcion")));
                o.put("solicitante", txt(rs.getString("solicitante")));
                o.put("centro_solicitante", txt(rs.getString("centro_solicitante")));
                o.put("responsable", txt(rs.getString("responsable")));
                o.put("es_mia", rs.getInt("id_usuario_solic") == idUsuario);
                o.put("a_mi_cargo", rs.getInt("id_usuario_responsable") == idUsuario);
                o.put("puede_gestionar", permisos.veCentro(rs.getInt("id_centro")) || rs.getInt("id_usuario_solic") == idUsuario);
                o.put("puede_eliminar", puedeEliminar(permisos, rs.getInt("id_usuario_solic"), idUsuario));
                o.put("puede_reabrir", "FINALIZADA".equals(rs.getString("estado")) && puedeReabrir(permisos, rs.getInt("id_usuario_finaliza"), idUsuario));
                o.put("items", rs.getInt("items"));
                o.put("unidades", rs.getInt("unidades"));
                o.put("sin_salida", rs.getInt("sin_salida"));
                o.put("salidas", rs.getInt("salidas"));
                o.put("finalizable", rs.getInt("sin_salida") == 0 && rs.getInt("salidas") > 0
                        && ("PENDIENTE".equals(rs.getString("estado")) || "EN_PROCESO".equals(rs.getString("estado"))));
                o.put("fecha_creacion", fechaHora(rs.getTimestamp("fecha_creacion")));
                o.put("fecha_finalizacion", fechaHora(rs.getTimestamp("fecha_finalizacion")));
                o.put("fecha_anulacion_fin", fechaHora(rs.getTimestamp("fecha_anulacion_fin")));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }

        // ---------- DETALLE DE UNA OT (JSON): cabecera, insumos e historial ----------
        else if ("detalle".equals(tipo)) {
            int idOT = numero(request.getParameter("id"));
            JSONObject f = new JSONObject();
            ps = conn.prepareStatement(
                "SELECT o.*, c.nombre AS centro, cc.codigo AS cc_codigo, cc.descripcion AS cc_descripcion, " +
                "       u.nombre || ' ' || u.apellido AS solicitante, u.email AS solicitante_email, usc.nombre AS centro_solicitante, " +
                "       ur.nombre || ' ' || ur.apellido AS responsable, ur.email AS responsable_email, " +
                "       uf.nombre || ' ' || uf.apellido AS finalizado_por, " +
                "       ua.nombre || ' ' || ua.apellido AS anulado_por " +
                "FROM ot o " +
                "LEFT JOIN centros c ON c.id_centro = o.id_centro " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = o.id_centro_costo " +
                "LEFT JOIN usuarios u ON u.id_usuario = o.id_usuario_solic " +
                "LEFT JOIN centros usc ON usc.id_centro = u.centro_asignado " +
                "LEFT JOIN usuarios ur ON ur.id_usuario = o.id_usuario_responsable " +
                "LEFT JOIN usuarios uf ON uf.id_usuario = o.id_usuario_finaliza " +
                "LEFT JOIN usuarios ua ON ua.id_usuario = o.id_usuario_anula_fin " +
                "WHERE o.id_ot = ?");
            ps.setInt(1, idOT);
            rs = ps.executeQuery();
            if (!rs.next()) {
                f.put("error", "Orden de trabajo no encontrada");
                responderJson(response, out, f);
                return;
            }
            f.put("id", rs.getInt("id_ot"));
            f.put("numero", txt(rs.getString("numero_ot")));
            f.put("fecha", rs.getDate("fecha_solicitud") == null ? "" : rs.getDate("fecha_solicitud").toString());
            f.put("titulo", txt(rs.getString("titulo")));
            f.put("descripcion", txt(rs.getString("descripcion")));
            f.put("estado", txt(rs.getString("estado")));
            f.put("id_centro", rs.getInt("id_centro"));
            boolean deSuCentro = permisos.veCentro(rs.getInt("id_centro"));
            boolean laPidio = rs.getInt("id_usuario_solic") == idUsuario;
            boolean aSuCargo = rs.getInt("id_usuario_responsable") == idUsuario;
            if (!deSuCentro && !laPidio && !aSuCargo) {
                f = new JSONObject();
                f.put("error", "La orden de trabajo es de un centro al que no tiene acceso");
                responderJson(response, out, f);
                return;
            }
            // gestionar = modificar (centro destino o quien la pidió); eliminar = quien la creó o el administrador;
            // operar = suministrar / finalizar (centro destino); reabrir = quien la finalizó o el administrador
            f.put("puede_gestionar", deSuCentro || laPidio);
            f.put("puede_eliminar", puedeEliminar(permisos, rs.getInt("id_usuario_solic"), idUsuario));
            f.put("puede_reabrir", "FINALIZADA".equals(rs.getString("estado")) && puedeReabrir(permisos, rs.getInt("id_usuario_finaliza"), idUsuario));
            f.put("puede_operar", deSuCentro);
            f.put("es_mia", laPidio);
            f.put("a_mi_cargo", aSuCargo);
            int idResp = rs.getInt("id_usuario_responsable");
            f.put("id_responsable", rs.wasNull() ? 0 : idResp);
            f.put("responsable", txt(rs.getString("responsable")));
            f.put("responsable_email", txt(rs.getString("responsable_email")));
            f.put("centro_solicitante", txt(rs.getString("centro_solicitante")));
            f.put("id_centro_costo", rs.getInt("id_centro_costo"));
            f.put("centro", txt(rs.getString("centro")));
            f.put("centro_costo", nombreCC(rs.getString("cc_codigo"), rs.getString("cc_descripcion")));
            f.put("solicitante", txt(rs.getString("solicitante")));
            f.put("solicitante_email", txt(rs.getString("solicitante_email")));
            f.put("fecha_creacion", fechaHora(rs.getTimestamp("fecha_creacion")));
            f.put("fecha_finalizacion", fechaHora(rs.getTimestamp("fecha_finalizacion")));
            f.put("finalizado_por", txt(rs.getString("finalizado_por")));
            f.put("observacion_finalizacion", txt(rs.getString("observacion_finalizacion")));
            f.put("fecha_anulacion_fin", fechaHora(rs.getTimestamp("fecha_anulacion_fin")));
            f.put("anulado_por", txt(rs.getString("anulado_por")));
            f.put("motivo_anulacion_fin", txt(rs.getString("motivo_anulacion_fin")));
            rs.close(); ps.close(); rs = null; ps = null;

            JSONArray items = new JSONArray();
            ps = conn.prepareStatement(
                "SELECT d.id_detalle_ot, d.cantidad, d.precio_unitario, d.subtotal, d.fecha_suministro, " +
                "       i.codigo, i.descripcion, um.abreviatura, a.nombre AS almacen, ca.nombre AS centro_almacen, " +
                "       us.nombre || ' ' || us.apellido AS suministrado_por, r.numero_remision " +
                "FROM detalle_ot d " +
                "LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false " +
                "LEFT JOIN insumos i ON i.id_insumo = d.id_insumo " +
                "LEFT JOIN unidad_medida um ON um.id_unidad_medida = i.id_unidad_medida " +
                "LEFT JOIN almacenes a ON a.id_almacen = d.id_almacen " +
                "LEFT JOIN centros ca ON ca.id_centro = a.id_centro " +
                "LEFT JOIN usuarios us ON us.id_usuario = d.id_usuario_suministra " +
                "WHERE d.id_ot = ? ORDER BY d.id_detalle_ot");
            ps.setInt(1, idOT);
            rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject it = new JSONObject();
                Timestamp fs = rs.getTimestamp("fecha_suministro");
                it.put("id", rs.getInt("id_detalle_ot"));
                it.put("codigo", txt(rs.getString("codigo")));
                it.put("descripcion", txt(rs.getString("descripcion")));
                it.put("unidad", txt(rs.getString("abreviatura")));
                it.put("cantidad", rs.getInt("cantidad"));
                it.put("almacen", txt(rs.getString("almacen")));
                it.put("centro_almacen", txt(rs.getString("centro_almacen")));
                it.put("fecha", fs == null ? "" : fs.toString().substring(0, 16));
                it.put("suministrado_por", txt(rs.getString("suministrado_por")));
                it.put("subtotal", rs.getBigDecimal("subtotal") == null ? "0" : rs.getBigDecimal("subtotal").toPlainString());
                it.put("remision", txt(rs.getString("numero_remision")));
                items.put(it);
            }
            f.put("items", items);
            rs.close(); ps.close(); rs = null; ps = null;

            JSONArray remisiones = new JSONArray();
            ps = conn.prepareStatement(
                "SELECT r.id_remision, r.numero_remision, r.tipo_remision, r.fecha_hora, r.anulada, r.motivo_anulacion, " +
                "       TRIM(COALESCE(r.transportista_nombre, '') || ' ' || COALESCE(r.transportista_apellido, '')) AS transportista " +
                "FROM remisiones r WHERE r.id_ot = ? ORDER BY r.fecha_hora DESC, r.id_remision DESC");
            ps.setInt(1, idOT);
            rs = ps.executeQuery();
            boolean haySalida = false;
            while (rs.next()) {
                JSONObject rm = new JSONObject();
                rm.put("id", rs.getInt("id_remision"));
                rm.put("numero", txt(rs.getString("numero_remision")));
                rm.put("tipo", txt(rs.getString("tipo_remision")));
                rm.put("fecha", fechaHora(rs.getTimestamp("fecha_hora")));
                rm.put("anulada", rs.getBoolean("anulada"));
                rm.put("motivo_anulacion", txt(rs.getString("motivo_anulacion")));
                rm.put("transportista", txt(rs.getString("transportista")));
                if (!rs.getBoolean("anulada")) {
                    haySalida = true;
                }
                remisiones.put(rm);
            }
            f.put("remisiones", remisiones);
            rs.close(); ps.close(); rs = null; ps = null;
            boolean pendientes = existe(conn, SQL_PENDIENTES_SALIDA, idOT, 0);
            f.put("pendientes_salida", pendientes);
            f.put("finalizable", haySalida && !pendientes && ("PENDIENTE".equals(f.getString("estado")) || "EN_PROCESO".equals(f.getString("estado"))));

            JSONArray eventos = new JSONArray();
            ps = conn.prepareStatement(
                "SELECT e.fecha, e.tipo, e.detalle, u.nombre || ' ' || u.apellido AS usuario " +
                "FROM ot_eventos e LEFT JOIN usuarios u ON u.id_usuario = e.id_usuario " +
                "WHERE e.id_ot = ? ORDER BY e.fecha DESC, e.id_evento DESC");
            ps.setInt(1, idOT);
            rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject ev = new JSONObject();
                ev.put("fecha", rs.getTimestamp("fecha").toString().substring(0, 16));
                ev.put("tipo", txt(rs.getString("tipo")));
                ev.put("detalle", txt(rs.getString("detalle")));
                ev.put("usuario", txt(rs.getString("usuario")));
                eventos.put(ev);
            }
            f.put("eventos", eventos);
            responderJson(response, out, f);
        }

        // ---------- MODIFICAR OT (título, descripción, centro de costo) ----------
        else if ("modificar".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idOT = numero(request.getParameter("id"));
                String titulo = limpio(request.getParameter("titulo"));
                String descripcion = limpio(request.getParameter("descripcion"));
                int idCC = numero(request.getParameter("id_centro_costo"));
                if (titulo.isEmpty()) {
                    throw new IllegalArgumentException("Ingrese un título para la orden de trabajo");
                }
                if (titulo.length() > 200 || descripcion.length() > 2000) {
                    throw new IllegalArgumentException("El título admite hasta 200 caracteres y la descripción hasta 2000");
                }
                String[] ot = bloquearOT(conn, idOT);
                exigirGestion(permisos, ot, idUsuario);
                exigirAbierta(ot[0]);
                int centroActual = Integer.parseInt(ot[1]);
                // Centro destino: se puede cambiar mientras la OT no tenga insumos entregados
                int idCentro = numero(request.getParameter("id_centro"));
                if (idCentro == 0) {
                    idCentro = centroActual;
                }
                boolean cambiaCentro = idCentro != centroActual;
                if (cambiaCentro) {
                    if (existe(conn, "SELECT 1 FROM detalle_ot WHERE id_ot = ?", idOT, 0)) {
                        throw new IllegalArgumentException("La OT ya tiene insumos entregados: no se puede cambiar el centro destino");
                    }
                    if (!existe(conn, "SELECT 1 FROM centros WHERE id_centro = ? AND activo = true", idCentro, 0)) {
                        throw new IllegalArgumentException("Seleccione un centro destino activo");
                    }
                }
                if (idCC == 0 || !existe(conn, "SELECT 1 FROM centros_costo WHERE id_centro_costo = ? AND id_centro = ? AND activo = true", idCC, idCentro)) {
                    throw new IllegalArgumentException("Seleccione un centro de costo del centro destino");
                }
                // Responsable: opcional (vacío = sin responsable); debe trabajar en el centro destino
                int idResponsable = numero(request.getParameter("id_responsable"));
                int responsableActual = ot[5] == null ? 0 : Integer.parseInt(ot[5]);
                if (idResponsable != 0 && idResponsable != responsableActual && !responsableValido(conn, idResponsable, idCentro)) {
                    throw new IllegalArgumentException("El responsable elegido no trabaja en el centro destino (o no está activo)");
                }
                if (idResponsable != 0 && cambiaCentro && !responsableValido(conn, idResponsable, idCentro)) {
                    throw new IllegalArgumentException("El responsable no trabaja en el nuevo centro destino: elija otro o déjelo vacío");
                }
                // Valores actuales, para registrar en el historial solo lo que cambió
                ps = conn.prepareStatement("SELECT titulo, COALESCE(descripcion, '') FROM ot WHERE id_ot = ?");
                ps.setInt(1, idOT);
                rs = ps.executeQuery();
                rs.next();
                boolean cambianDatos = !titulo.equals(rs.getString(1)) || !descripcion.equals(rs.getString(2))
                        || ot[3] == null || idCC != Integer.parseInt(ot[3]);
                rs.close(); ps.close(); rs = null; ps = null;
                ps = conn.prepareStatement("UPDATE ot SET titulo = ?, descripcion = ?, id_centro_costo = ?, id_centro = ?, id_usuario_responsable = ? WHERE id_ot = ?");
                ps.setString(1, titulo);
                if (descripcion.isEmpty()) { ps.setNull(2, Types.VARCHAR); } else { ps.setString(2, descripcion); }
                ps.setInt(3, idCC);
                ps.setInt(4, idCentro);
                if (idResponsable == 0) { ps.setNull(5, Types.INTEGER); } else { ps.setInt(5, idResponsable); }
                ps.setInt(6, idOT);
                ps.executeUpdate();
                ps.close(); ps = null;
                if (cambianDatos) {
                    evento(conn, idOT, "MODIFICACION", idUsuario, "Datos modificados. Título: " + titulo);
                }
                if (cambiaCentro) {
                    evento(conn, idOT, "CAMBIO_CENTRO", idUsuario, "Centro destino: " + nombreDe(conn, "centros", "id_centro", centroActual)
                            + " -> " + nombreDe(conn, "centros", "id_centro", idCentro));
                }
                if (idResponsable != responsableActual) {
                    evento(conn, idOT, "RESPONSABLE", idUsuario, "Responsable: "
                            + (responsableActual == 0 ? "(sin responsable)" : nombreUsuario(conn, responsableActual)) + " -> "
                            + (idResponsable == 0 ? "(sin responsable)" : nombreUsuario(conn, idResponsable)));
                }
                conn.commit();
                resp.put("ok", true);
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // ---------- ELIMINAR OT (solo si no tiene insumos suministrados) ----------
        else if ("eliminar".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idOT = numero(request.getParameter("id"));
                String[] ot = bloquearOT(conn, idOT);
                if (!puedeEliminar(permisos, ot[4] == null ? 0 : Integer.parseInt(ot[4]), idUsuario)) {
                    throw new IllegalArgumentException("Solo quien creó la orden de trabajo o el administrador pueden eliminarla");
                }
                if ("FINALIZADA".equals(ot[0])) {
                    throw new IllegalArgumentException("No se puede eliminar una OT finalizada");
                }
                if (existe(conn, "SELECT 1 FROM remisiones WHERE id_ot = ?", idOT, 0)) {
                    throw new IllegalArgumentException("La OT tiene remisiones registradas y no se puede eliminar (se conserva por trazabilidad).");
                }
                if (existe(conn, "SELECT 1 FROM detalle_ot WHERE id_ot = ?", idOT, 0)) {
                    throw new IllegalArgumentException("La OT tiene insumos suministrados. Quítelos primero en \"Suministrar OT\" (el stock vuelve al almacén) y luego elimínela.");
                }
                ps = conn.prepareStatement("DELETE FROM ot WHERE id_ot = ?");
                ps.setInt(1, idOT);
                ps.executeUpdate();
                ps.close(); ps = null;
                conn.commit();
                resp.put("ok", true);
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // ---------- COMBOS PARA SUMINISTRAR ----------
        else if ("listarAbiertas".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_ot, numero_ot, titulo, estado FROM ot WHERE estado IN ('PENDIENTE', 'EN_PROCESO') AND " + permisos.sqlCentros("id_centro") + " ORDER BY fecha_solicitud, id_ot");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar orden de trabajo --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt("id_ot") + "'>" + Html.esc(rs.getString("numero_ot")) + " - " + Html.esc(rs.getString("titulo"))
                        + ("EN_PROCESO".equals(rs.getString("estado")) ? " (en proceso)" : "") + "</option>");
            }
        }
        else if ("listarInsumos".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT i.id_insumo, i.codigo, i.descripcion, COALESCE(SUM(s.stock_actual), 0) AS total " +
                "FROM insumos i LEFT JOIN stock_almacen s ON s.id_insumo = i.id_insumo " +
                "WHERE i.activo = true GROUP BY i.id_insumo, i.codigo, i.descripcion ORDER BY i.descripcion");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar insumo --</option>");
            while (rs.next()) {
                long total = rs.getLong("total");
                out.print("<option value='" + rs.getInt("id_insumo") + "' data-codigo='" + Html.esc(rs.getString("codigo")) + "' data-nombre='" + Html.esc(rs.getString("descripcion")) + "'"
                        + (total <= 0 ? " disabled" : "") + ">"
                        + Html.esc(rs.getString("codigo")) + " - " + Html.esc(rs.getString("descripcion"))
                        + (total <= 0 ? " (sin stock)" : " (disponible: " + total + ")") + "</option>");
            }
        }
        else if ("almacenesConStock".equals(tipo)) {
            int idInsumo = numero(request.getParameter("id_insumo"));
            ps = conn.prepareStatement(
                "SELECT a.id_almacen, a.nombre AS almacen, c.nombre AS centro, s.stock_actual " +
                "FROM stock_almacen s INNER JOIN almacenes a ON a.id_almacen = s.id_almacen " +
                "LEFT JOIN centros c ON c.id_centro = a.id_centro " +
                "WHERE s.id_insumo = ? AND s.stock_actual > 0 AND a.activo = true AND " + permisos.sqlCentros("a.id_centro") + " ORDER BY c.nombre, a.nombre");
            ps.setInt(1, idInsumo);
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject a = new JSONObject();
                a.put("id", rs.getInt("id_almacen"));
                a.put("almacen", txt(rs.getString("almacen")));
                a.put("centro", txt(rs.getString("centro")));
                a.put("stock", rs.getInt("stock_actual"));
                lista.put(a);
            }
            responderJson(response, out, lista);
        }

        // ---------- SUMINISTRAR (descuenta stock) ----------
        // items = [{id_insumo, id_almacen, cantidad}]
        else if ("suministrar".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idOT = numero(request.getParameter("id_ot"));
                JSONArray items = new JSONArray(request.getParameter("items"));
                if (items.length() == 0) {
                    throw new IllegalArgumentException("Agregue al menos un insumo");
                }
                String[] ot = bloquearOT(conn, idOT);
                permisos.exigirCentro(Integer.parseInt(ot[1]));
                exigirAbierta(ot[0]);
                String numeroOT = ot[2];
                Integer ccOT = ot[3] == null ? null : Integer.valueOf(ot[3]);

                StringBuilder resumen = new StringBuilder();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    int idInsumo = it.optInt("id_insumo", 0);
                    int idAlmacen = it.optInt("id_almacen", 0);
                    int cantidad = it.optInt("cantidad", 0);
                    if (idInsumo <= 0 || idAlmacen <= 0) {
                        throw new IllegalArgumentException("Cada línea debe tener insumo y almacén");
                    }
                    if (cantidad <= 0) {
                        throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
                    }
                    if (!existe(conn, "SELECT 1 FROM almacenes WHERE id_almacen = ? AND " + permisos.sqlCentros("id_centro"), idAlmacen, 0)) {
                        throw new IllegalArgumentException("El almacén de salida no pertenece a sus centros");
                    }
                    // Datos del insumo y precio para el costo de la OT
                    ps = conn.prepareStatement("SELECT codigo, descripcion, COALESCE(precio_compra, 0) FROM insumos WHERE id_insumo = ? AND activo = true");
                    ps.setInt(1, idInsumo);
                    rs = ps.executeQuery();
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Uno de los insumos no existe o está inactivo");
                    }
                    String codigo = rs.getString(1);
                    String nombre = rs.getString(2);
                    BigDecimal precio = rs.getBigDecimal(3);
                    rs.close(); ps.close(); rs = null; ps = null;

                    // Salida de stock + Kardex (bloquea el stock y no permite dejarlo negativo)
                    MovimientoStock salida = new MovimientoStock(MovimientoStock.SALIDA_OT, idInsumo, idAlmacen, -cantidad, idUsuario);
                    salida.documento = numeroOT;
                    salida.idOt = Integer.valueOf(idOT);
                    salida.idCentroCosto = ccOT;
                    salida.costoUnitario = precio;
                    salida.observacion = "Suministro a la OT";
                    salida.registrar(conn);
                    String almacen = nombreAlmacen(conn, idAlmacen);

                    ps = conn.prepareStatement(
                        "INSERT INTO detalle_ot (id_ot, id_insumo, cantidad, id_almacen, precio_unitario, fecha_suministro, id_usuario_suministra) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)");
                    ps.setInt(1, idOT);
                    ps.setInt(2, idInsumo);
                    ps.setInt(3, cantidad);
                    ps.setInt(4, idAlmacen);
                    ps.setBigDecimal(5, precio);
                    ps.setInt(6, idUsuario);
                    ps.executeUpdate();
                    ps.close(); ps = null;

                    resumen.append(resumen.length() == 0 ? "" : "; ").append(cantidad).append(" x ").append(codigo).append(" - ").append(nombre).append(" (").append(almacen).append(")");
                }

                ps = conn.prepareStatement("UPDATE ot SET estado = 'EN_PROCESO' WHERE id_ot = ?");
                ps.setInt(1, idOT);
                ps.executeUpdate();
                ps.close(); ps = null;
                evento(conn, idOT, "SUMINISTRO", idUsuario, "Insumos entregados: " + resumen);
                conn.commit();
                resp.put("ok", true);
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } catch (Exception e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", "Error al suministrar: " + Errores.mensaje(application, e));
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // ---------- QUITAR UNA LÍNEA SUMINISTRADA (el stock vuelve al almacén) ----------
        else if ("quitarSuministro".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idDetalle = numero(request.getParameter("id_detalle"));
                ps = conn.prepareStatement(
                    "SELECT d.id_ot, d.id_insumo, d.id_almacen, d.cantidad, d.precio_unitario, i.codigo, i.descripcion, r.numero_remision " +
                    "FROM detalle_ot d LEFT JOIN insumos i ON i.id_insumo = d.id_insumo " +
                    "LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false " +
                    "WHERE d.id_detalle_ot = ?");
                ps.setInt(1, idDetalle);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("La línea ya no existe");
                }
                int idOT = rs.getInt("id_ot");
                int idInsumo = rs.getInt("id_insumo");
                int idAlmacen = rs.getInt("id_almacen");
                int cantidad = rs.getInt("cantidad");
                String texto = cantidad + " x " + txt(rs.getString("codigo")) + " - " + txt(rs.getString("descripcion"));
                BigDecimal precioLinea = rs.getBigDecimal("precio_unitario");
                String remisionLinea = rs.getString("numero_remision");
                rs.close(); ps.close(); rs = null; ps = null;

                String[] ot = bloquearOT(conn, idOT);
                permisos.exigirCentro(Integer.parseInt(ot[1]));
                exigirAbierta(ot[0]);
                if (remisionLinea != null) {
                    throw new IllegalArgumentException("Este insumo ya salió con la remisión " + remisionLinea
                            + ". Para quitarlo, primero anule la remisión en \"Salida de insumos\".");
                }

                // El stock vuelve al almacén (queda en el Kardex como devolución)
                MovimientoStock devolucion = new MovimientoStock(MovimientoStock.DEVOLUCION_OT, idInsumo, idAlmacen, cantidad, idUsuario);
                devolucion.documento = ot[2];
                devolucion.idOt = Integer.valueOf(idOT);
                devolucion.idCentroCosto = ot[3] == null ? null : Integer.valueOf(ot[3]);
                devolucion.costoUnitario = precioLinea;
                devolucion.observacion = "Insumo quitado de la OT";
                devolucion.registrar(conn);

                ps = conn.prepareStatement("DELETE FROM detalle_ot WHERE id_detalle_ot = ?");
                ps.setInt(1, idDetalle);
                ps.executeUpdate();
                ps.close(); ps = null;

                // Sin líneas, la OT vuelve a PENDIENTE
                ps = conn.prepareStatement(
                    "UPDATE ot SET estado = CASE WHEN EXISTS (SELECT 1 FROM detalle_ot WHERE id_ot = ?) THEN 'EN_PROCESO' ELSE 'PENDIENTE' END WHERE id_ot = ?");
                ps.setInt(1, idOT);
                ps.setInt(2, idOT);
                ps.executeUpdate();
                ps.close(); ps = null;

                evento(conn, idOT, "QUITAR_SUMINISTRO", idUsuario, "Se quitó " + texto + " (el stock volvió al almacén)");
                conn.commit();
                resp.put("ok", true);
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // ---------- FINALIZAR ----------
        else if ("finalizar".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idOT = numero(request.getParameter("id"));
                String observacion = limpio(request.getParameter("observacion"));
                String[] ot = bloquearOT(conn, idOT);
                permisos.exigirCentro(Integer.parseInt(ot[1]));
                exigirAbierta(ot[0]);
                // Regla: no puede quedar ningún insumo entregado sin su salida (remisión)...
                if (existe(conn, SQL_PENDIENTES_SALIDA, idOT, 0)) {
                    throw new IllegalArgumentException("La OT tiene insumos suministrados que todavía no salieron. Registre la salida en \"Salida de insumos\" antes de finalizar.");
                }
                // ...y debe tener al menos una salida registrada: de insumos o de activos
                if (!existe(conn, "SELECT 1 FROM remisiones WHERE id_ot = ? AND anulada = false AND tipo_remision IN ('SALIDA_INSUMOS', 'SALIDA_ACTIVOS')", idOT, 0)) {
                    throw new IllegalArgumentException("La OT no tiene ninguna salida registrada (de insumos o de activos). Registre la salida antes de finalizar.");
                }
                ps = conn.prepareStatement(
                    "UPDATE ot SET estado = 'FINALIZADA', fecha_finalizacion = CURRENT_TIMESTAMP, id_usuario_finaliza = ?, observacion_finalizacion = ? WHERE id_ot = ?");
                ps.setInt(1, idUsuario);
                if (observacion.isEmpty()) { ps.setNull(2, Types.VARCHAR); } else { ps.setString(2, observacion); }
                ps.setInt(3, idOT);
                ps.executeUpdate();
                ps.close(); ps = null;
                evento(conn, idOT, "FINALIZACION", idUsuario, observacion.isEmpty() ? "OT finalizada" : "OT finalizada: " + observacion);
                conn.commit();
                resp.put("ok", true);
                resp.put("fecha", fechaActual(conn));
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // ---------- ANULAR LA FINALIZACIÓN (reabrir, con motivo obligatorio) ----------
        else if ("reabrir".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idOT = numero(request.getParameter("id"));
                String motivo = limpio(request.getParameter("motivo"));
                if (motivo.isEmpty()) {
                    throw new IllegalArgumentException("Indique el motivo para anular la finalización");
                }
                String[] ot = bloquearOT(conn, idOT);
                permisos.exigirCentro(Integer.parseInt(ot[1]));
                if (!"FINALIZADA".equals(ot[0])) {
                    throw new IllegalArgumentException("La OT no está finalizada");
                }
                if (!puedeReabrir(permisos, ot[6] == null ? 0 : Integer.parseInt(ot[6]), idUsuario)) {
                    throw new IllegalArgumentException("Solo quien finalizó la orden de trabajo o el administrador pueden anular la finalización");
                }
                ps = conn.prepareStatement(
                    "UPDATE ot SET estado = CASE WHEN EXISTS (SELECT 1 FROM detalle_ot WHERE id_ot = ?) THEN 'EN_PROCESO' ELSE 'PENDIENTE' END, " +
                    "fecha_finalizacion = NULL, id_usuario_finaliza = NULL, observacion_finalizacion = NULL, " +
                    "fecha_anulacion_fin = CURRENT_TIMESTAMP, id_usuario_anula_fin = ?, motivo_anulacion_fin = ? WHERE id_ot = ?");
                ps.setInt(1, idOT);
                ps.setInt(2, idUsuario);
                ps.setString(3, motivo);
                ps.setInt(4, idOT);
                ps.executeUpdate();
                ps.close(); ps = null;
                evento(conn, idOT, "REAPERTURA", idUsuario, "Finalización anulada: " + motivo);
                conn.commit();
                resp.put("ok", true);
                resp.put("fecha", fechaActual(conn));
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        else {
            out.print("error: Operación no reconocida");
        }
    } catch (Exception e) {
        JSONObject err = new JSONObject();
        err.put("ok", false);
        err.put("error", "Error: " + Errores.mensaje(application, e));
        responderJson(response, out, err);
        e.printStackTrace();
    } finally {
        if (rs != null) try { rs.close(); } catch (Exception e) {}
        if (ps != null) try { ps.close(); } catch (Exception e) {}
    }

    }


    /** Insumos suministrados a la OT que todavía no tienen salida (remisión vigente). */
    private static final String SQL_PENDIENTES_SALIDA =
        "SELECT 1 FROM detalle_ot d LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false " +
        "WHERE d.id_ot = ? AND r.id_remision IS NULL";

    private static void responderJson(HttpServletResponse response, PrintWriter out, Object json) throws java.io.IOException {
        response.setContentType("application/json; charset=UTF-8");
        out.print(json.toString());
    }

    /** Bloquea la OT (FOR UPDATE) y devuelve {estado, id_centro, numero_ot, id_centro_costo, solicitante, responsable, finaliza}. */
    private static String[] bloquearOT(Connection conn, int idOT) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT estado, id_centro, numero_ot, id_centro_costo, id_usuario_solic, id_usuario_responsable, id_usuario_finaliza FROM ot WHERE id_ot = ? FOR UPDATE");
        try {
            p.setInt(1, idOT);
            ResultSet r = p.executeQuery();
            if (!r.next()) {
                r.close();
                throw new IllegalArgumentException("Orden de trabajo no encontrada");
            }
            // [0] estado, [1] centro destino, [2] número, [3] centro de costo, [4] solicitante, [5] responsable (null si no hay),
            // [6] quien la finalizó (null si no está finalizada)
            int cc = r.getInt(4);
            String ccTxt = r.wasNull() ? null : String.valueOf(cc);
            int sol = r.getInt(5);
            String solTxt = r.wasNull() ? null : String.valueOf(sol);
            int resp = r.getInt(6);
            String respTxt = r.wasNull() ? null : String.valueOf(resp);
            int fin = r.getInt(7);
            String finTxt = r.wasNull() ? null : String.valueOf(fin);
            String[] res = {r.getString(1), String.valueOf(r.getInt(2)), r.getString(3), ccTxt, solTxt, respTxt, finTxt};
            r.close();
            return res;
        } finally {
            p.close();
        }
    }

    /** Usuarios que pueden ser responsables de una OT: activos y de un rol que trabaja OT. */
    private static final String SQL_RESPONSABLE_POSIBLE =
        "u.activo = true AND r.codigo IN ('ADMINISTRADOR', 'GERENTE', 'AUXILIAR')";

    /** true si el usuario puede ser responsable de una OT del centro indicado. */
    private static boolean responsableValido(Connection conn, int idResp, int idCentro) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
            "SELECT 1 FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol AND r.activo = true " +
            "WHERE u.id_usuario = ? AND " + SQL_RESPONSABLE_POSIBLE + " AND (u.centro_asignado = ? OR EXISTS (SELECT 1 FROM usuario_centros uc " +
            "  WHERE uc.id_usuario = u.id_usuario AND uc.id_centro = ? AND uc.fecha_desde <= CURRENT_DATE " +
            "    AND (uc.fecha_hasta IS NULL OR uc.fecha_hasta >= CURRENT_DATE)))");
        try {
            p.setInt(1, idResp);
            p.setInt(2, idCentro);
            p.setInt(3, idCentro);
            ResultSet r = p.executeQuery();
            boolean ok = r.next();
            r.close();
            return ok;
        } finally {
            p.close();
        }
    }

    /**
     * OT que el usuario puede ver: las de sus centros (centro destino), las que pidió
     * y las que tiene a cargo. Solo contiene números enteros (sin riesgo de inyección).
     */
    private static String condVisible(Permisos p, int idUsuario) {
        return "(" + p.sqlCentros("o.id_centro") + " OR o.id_usuario_solic = " + idUsuario
                + " OR o.id_usuario_responsable = " + idUsuario + ")";
    }

    /** Eliminar: solo quien creó la OT o el administrador. */
    private static boolean puedeEliminar(Permisos p, int idSolicitante, int idUsuario) {
        return p.esAdministrador() || (idSolicitante > 0 && idSolicitante == idUsuario);
    }

    /** Anular la finalización: solo quien la finalizó o el administrador. */
    private static boolean puedeReabrir(Permisos p, int idFinaliza, int idUsuario) {
        return p.esAdministrador() || (idFinaliza > 0 && idFinaliza == idUsuario);
    }

    /** Modificar: el centro destino o quien pidió la OT. */
    private static void exigirGestion(Permisos p, String[] ot, int idUsuario) {
        boolean laPidio = ot[4] != null && Integer.parseInt(ot[4]) == idUsuario;
        if (!laPidio && !p.veCentro(Integer.parseInt(ot[1]))) {
            throw new IllegalArgumentException("Solo el centro destino o quien pidió la orden de trabajo pueden modificarla");
        }
    }

    private static String nombreUsuario(Connection conn, int id) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT nombre || ' ' || apellido FROM usuarios WHERE id_usuario = ?");
        try {
            p.setInt(1, id);
            ResultSet r = p.executeQuery();
            String n = r.next() ? r.getString(1) : ("usuario " + id);
            r.close();
            return n;
        } finally {
            p.close();
        }
    }

    /** Nombre de un registro (tabla y columna fijas del código, nunca del usuario). */
    private static String nombreDe(Connection conn, String tabla, String columnaId, int id) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT nombre FROM " + tabla + " WHERE " + columnaId + " = ?");
        try {
            p.setInt(1, id);
            ResultSet r = p.executeQuery();
            String n = r.next() ? r.getString(1) : String.valueOf(id);
            r.close();
            return n;
        } finally {
            p.close();
        }
    }

    private static void exigirAbierta(String estado) {
        if (!"PENDIENTE".equals(estado) && !"EN_PROCESO".equals(estado)) {
            throw new IllegalArgumentException("La OT está " + estado + " y no se puede modificar. "
                    + ("FINALIZADA".equals(estado) ? "Anule la finalización en \"Finalizar OT\" para volver a usarla." : ""));
        }
    }

    private static String nombreAlmacen(Connection conn, int idAlmacen) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT nombre FROM almacenes WHERE id_almacen = ?");
        try {
            p.setInt(1, idAlmacen);
            ResultSet r = p.executeQuery();
            String n = r.next() ? r.getString(1) : "";
            r.close();
            return n;
        } finally {
            p.close();
        }
    }

    private static int centroDelUsuario(Connection conn, int idUsuario) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT centro_asignado FROM usuarios WHERE id_usuario = ?");
        try {
            p.setInt(1, idUsuario);
            ResultSet r = p.executeQuery();
            int c = r.next() ? r.getInt(1) : 0;
            r.close();
            return c;
        } finally {
            p.close();
        }
    }

    private static void evento(Connection conn, int idOT, String tipo, int idUsuario, String detalle) throws SQLException {
        PreparedStatement p = conn.prepareStatement("INSERT INTO ot_eventos (id_ot, tipo, id_usuario, detalle) VALUES (?, ?, ?, ?)");
        try {
            p.setInt(1, idOT);
            p.setString(2, tipo);
            p.setInt(3, idUsuario);
            p.setString(4, detalle != null && detalle.length() > 4000 ? detalle.substring(0, 4000) : detalle);
            p.executeUpdate();
        } finally {
            p.close();
        }
    }

    /** SELECT 1 con uno o dos parámetros enteros (el segundo solo si el SQL lo tiene). */
    private static boolean existe(Connection conn, String sql, int p1, int p2) throws SQLException {
        PreparedStatement p = conn.prepareStatement(sql);
        try {
            p.setInt(1, p1);
            if (sql.indexOf('?') != sql.lastIndexOf('?')) {
                p.setInt(2, p2);
            }
            ResultSet r = p.executeQuery();
            boolean hay = r.next();
            r.close();
            return hay;
        } finally {
            p.close();
        }
    }

    private static String nombreCC(String codigo, String descripcion) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return "";
        }
        return (descripcion == null || descripcion.trim().isEmpty()) ? codigo : codigo + " - " + descripcion;
    }

    private static int numero(String v) {
        return (v != null && v.trim().matches("[0-9]{1,9}")) ? Integer.parseInt(v.trim()) : 0;
    }

    private static String limpio(String v) {
        return v == null ? "" : v.trim();
    }

    private static String txt(String s) {
        return s == null ? "" : s;
    }

    /** Timestamp -> "dd/MM/yyyy HH:mm" ("" si es null). */
    private static String fechaHora(Timestamp t) {
        return t == null ? "" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(t);
    }

    private static String fechaActual(Connection conn) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT CURRENT_TIMESTAMP");
        try {
            ResultSet r = p.executeQuery();
            r.next();
            String f = fechaHora(r.getTimestamp(1));
            r.close();
            return f;
        } finally {
            p.close();
        }
    }

}
