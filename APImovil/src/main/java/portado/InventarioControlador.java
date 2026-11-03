package portado;

import inventario.MovimientoStock;
import inventario.Remisiones;
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
 * Copia de la web: Controlador/inventarioControlador.jsp
 * La lógica (validaciones, transacciones, stock y Kardex) es la misma que usa el sistema web.
 * Generado por portar.py; solo se quitaron las operaciones de impresión en PDF.
 */
@SuppressWarnings({"unused", "rawtypes"})
public final class InventarioControlador {

    private InventarioControlador() {
    }

    public static void atender(HttpServletRequest request, HttpServletResponse response, PrintWriter out, Connection conn,
                               Permisos permisos, SesionDatos session, ServletContext application) throws Exception {

    String tipo = request.getParameter("tipo");
    PreparedStatement ps = null;
    ResultSet rs = null;
    int idUsuario = ((Integer) session.getAttribute("id_usuario")).intValue();

    try {
        // =================================================================
        // COMBOS
        // =================================================================
        if ("listarCentros".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_centro, nombre FROM centros WHERE activo = true AND " + permisos.sqlCentros("id_centro") + " ORDER BY nombre");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt(1) + "'>" + Html.esc(rs.getString(2)) + "</option>");
            }
        }
        else if ("listarCentrosCosto".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_centro_costo, codigo, descripcion FROM centros_costo WHERE id_centro = ? AND activo = true ORDER BY codigo");
            ps.setInt(1, numero(request.getParameter("id_centro")));
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt(1) + "'>" + Html.esc(nombreCC(rs.getString(2), rs.getString(3))) + "</option>");
            }
        }
        // Almacenes activos (de un centro, o todos con id_centro vacío)
        else if ("listarAlmacenes".equals(tipo)) {
            int idCentro = numero(request.getParameter("id_centro"));
            ps = conn.prepareStatement(
                "SELECT a.id_almacen, a.nombre, c.nombre FROM almacenes a LEFT JOIN centros c ON c.id_centro = a.id_centro " +
                "WHERE a.activo = true AND (? = 0 OR a.id_centro = ?) AND "
                // destino de una transferencia: puede ser un almacén de cualquier centro
                + ("true".equals(request.getParameter("destino")) ? "TRUE" : permisos.sqlCentros("a.id_centro")) + " ORDER BY c.nombre, a.nombre");
            ps.setInt(1, idCentro);
            ps.setInt(2, idCentro);
            rs = ps.executeQuery();
            out.print("<option value=''>" + ("true".equals(request.getParameter("todos")) ? "Todos los almacenes" : "-- Seleccionar --") + "</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt(1) + "'>" + Html.esc(rs.getString(2)) + (idCentro == 0 ? " (" + Html.esc(rs.getString(3)) + ")" : "") + "</option>");
            }
        }
        else if ("listarInsumos".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_insumo, codigo, descripcion FROM insumos WHERE activo = true ORDER BY descripcion");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar insumo --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt(1) + "' data-codigo='" + Html.esc(rs.getString(2)) + "' data-nombre='" + Html.esc(rs.getString(3)) + "'>"
                        + Html.esc(rs.getString(2)) + " - " + Html.esc(rs.getString(3)) + "</option>");
            }
        }
        // Stock de un almacén: [{id_insumo, codigo, nombre, unidad, stock}]
        else if ("stockAlmacen".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT i.id_insumo, i.codigo, i.descripcion, u.abreviatura, s.stock_actual " +
                "FROM stock_almacen s JOIN insumos i ON i.id_insumo = s.id_insumo " +
                "LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida " +
                "WHERE s.id_almacen = ? AND EXISTS (SELECT 1 FROM almacenes ax WHERE ax.id_almacen = s.id_almacen AND " + permisos.sqlCentros("ax.id_centro") + ") ORDER BY i.descripcion");
            ps.setInt(1, numero(request.getParameter("id_almacen")));
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id_insumo", rs.getInt(1));
                o.put("codigo", txt(rs.getString(2)));
                o.put("nombre", txt(rs.getString(3)));
                o.put("unidad", txt(rs.getString(4)));
                o.put("stock", rs.getInt(5));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }

        // =================================================================
        // SALIDA DE INSUMOS (OT) + REMISIÓN
        // =================================================================
        else if ("otsConPendientes".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT o.id_ot, o.numero_ot, o.titulo, COUNT(*) AS lineas FROM ot o " +
                "JOIN detalle_ot d ON d.id_ot = o.id_ot " +
                "LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false " +
                "WHERE o.estado IN ('PENDIENTE', 'EN_PROCESO') AND r.id_remision IS NULL AND " + permisos.sqlCentros("o.id_centro") + " " +
                "GROUP BY o.id_ot, o.numero_ot, o.titulo, o.fecha_solicitud ORDER BY o.fecha_solicitud, o.id_ot");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar orden de trabajo --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt(1) + "'>" + Html.esc(rs.getString(2)) + " - " + Html.esc(rs.getString(3))
                        + " (" + rs.getInt(4) + " insumo" + (rs.getInt(4) == 1 ? "" : "s") + " por despachar)</option>");
            }
        }
        // Cabecera de la OT + insumos pendientes de salida (solo lectura)
        else if ("pendientesSalida".equals(tipo)) {
            int idOT = numero(request.getParameter("id_ot"));
            JSONObject f = new JSONObject();
            ps = conn.prepareStatement(
                "SELECT o.numero_ot, o.titulo, o.estado, c.nombre AS centro, cc.codigo, cc.descripcion AS cc_desc, " +
                "       u.nombre || ' ' || u.apellido AS solicitante " +
                "FROM ot o LEFT JOIN centros c ON c.id_centro = o.id_centro " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = o.id_centro_costo " +
                "LEFT JOIN usuarios u ON u.id_usuario = o.id_usuario_solic WHERE o.id_ot = ? AND " + permisos.sqlCentros("o.id_centro"));
            ps.setInt(1, idOT);
            rs = ps.executeQuery();
            if (!rs.next()) {
                f.put("error", "Orden de trabajo no encontrada");
                responderJson(response, out, f);
                return;
            }
            f.put("numero", txt(rs.getString("numero_ot")));
            f.put("titulo", txt(rs.getString("titulo")));
            f.put("estado", txt(rs.getString("estado")));
            f.put("centro", txt(rs.getString("centro")));
            f.put("centro_costo", nombreCC(rs.getString("codigo"), rs.getString("cc_desc")));
            f.put("solicitante", txt(rs.getString("solicitante")));
            rs.close(); ps.close(); rs = null; ps = null;
            JSONArray items = new JSONArray();
            ps = conn.prepareStatement(
                "SELECT i.codigo, i.descripcion, u.abreviatura, d.cantidad, a.nombre AS almacen " +
                "FROM detalle_ot d JOIN insumos i ON i.id_insumo = d.id_insumo " +
                "LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida " +
                "LEFT JOIN almacenes a ON a.id_almacen = d.id_almacen " +
                "LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false " +
                "WHERE d.id_ot = ? AND r.id_remision IS NULL ORDER BY d.id_detalle_ot");
            ps.setInt(1, idOT);
            rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject it = new JSONObject();
                it.put("codigo", txt(rs.getString(1)));
                it.put("nombre", txt(rs.getString(2)));
                it.put("unidad", txt(rs.getString(3)));
                it.put("cantidad", rs.getInt(4));
                it.put("almacen", txt(rs.getString(5)));
                items.put(it);
            }
            f.put("items", items);
            responderJson(response, out, f);
        }
        else if ("emitirSalida".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idOT = numero(request.getParameter("id_ot"));
                Transportista tr = transportista(request, true);
                ps = conn.prepareStatement("SELECT estado, numero_ot FROM ot WHERE id_ot = ? AND " + permisos.sqlCentros("id_centro") + " FOR UPDATE");
                ps.setInt(1, idOT);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("Orden de trabajo no encontrada");
                }
                String estado = rs.getString(1);
                String numeroOT = rs.getString(2);
                rs.close(); ps.close(); rs = null; ps = null;
                if (!"PENDIENTE".equals(estado) && !"EN_PROCESO".equals(estado)) {
                    throw new IllegalArgumentException("La OT está " + estado + "; no se puede registrar la salida");
                }
                if (!existe(conn, SQL_PENDIENTES_SALIDA, idOT)) {
                    throw new IllegalArgumentException("La OT no tiene insumos pendientes de salida");
                }
                String numeroRem = siguiente(conn, "SELECT 'REM-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad(nextval('public.seq_remision')::text, 6, '0')");
                int idRem = insertarRemision(conn, numeroRem, "SALIDA_INSUMOS", Integer.valueOf(idOT), null, idUsuario, tr,
                                             limpio(request.getParameter("observacion")));
                ps = conn.prepareStatement(
                    "UPDATE detalle_ot d SET id_remision = ? WHERE d.id_ot = ? AND NOT EXISTS " +
                    "(SELECT 1 FROM remisiones r WHERE r.id_remision = d.id_remision AND r.anulada = false)");
                ps.setInt(1, idRem);
                ps.setInt(2, idOT);
                int lineas = ps.executeUpdate();
                ps.close(); ps = null;
                Remisiones.copiarDetalle(conn, idRem); // detalle propio de la remisión (remisiones_detalle)
                eventoOT(conn, idOT, "SALIDA", idUsuario, "Salida de " + lineas + " insumo(s) con la remisión " + numeroRem
                        + ". Transporta: " + tr.nombre + " " + tr.apellido + " (CI " + tr.ci + ")");
                conn.commit();
                resp.put("ok", true);
                resp.put("numero", numeroRem);
                resp.put("id_remision", idRem);
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

        // =================================================================
        // REMISIONES: listado, anulación e impresión
        // =================================================================
        else if ("listarRemisiones".equals(tipo)) {
            // tipo_remision: '' (todas) | INSUMOS (salidas de insumos + transferencias) | SALIDA_INSUMOS | TRANSFERENCIA | SALIDA_ACTIVOS
            // estado_remision: '' (todas) | VIGENTE | ANULADA
            String tr = request.getParameter("tipo_remision");
            if (!"SALIDA_INSUMOS".equals(tr) && !"TRANSFERENCIA".equals(tr) && !"SALIDA_ACTIVOS".equals(tr) && !"INSUMOS".equals(tr)) {
                tr = "";
            }
            String er = request.getParameter("estado_remision");
            if (!"VIGENTE".equals(er) && !"ANULADA".equals(er)) {
                er = "";
            }
            ps = conn.prepareStatement(
                "SELECT r.id_remision, r.numero_remision, r.tipo_remision, r.fecha_hora, r.anulada, r.motivo_anulacion, r.fecha_anulacion, " +
                "       TRIM(COALESCE(r.transportista_nombre, '') || ' ' || COALESCE(r.transportista_apellido, '')) AS transportista, r.transportista_ci, " +
                "       o.numero_ot, o.titulo, t.numero AS numero_tr, ao.nombre AS origen, ad.nombre AS destino, " +
                "       ue.nombre || ' ' || ue.apellido AS emitio, sa.numero AS numero_sa, sa.tipo AS tipo_sa, " +
                "       CASE r.tipo_remision WHEN 'TRANSFERENCIA' THEN (SELECT COUNT(*) FROM transferencias_detalle x WHERE x.id_transferencia = r.id_transferencia) " +
                "            WHEN 'SALIDA_ACTIVOS' THEN (SELECT COUNT(*) FROM solicitudes_activos_detalle x WHERE x.id_solicitud = r.id_solicitud_activo) " +
                "            ELSE (SELECT COUNT(*) FROM detalle_ot x WHERE x.id_remision = r.id_remision) END AS items " +
                "FROM remisiones r LEFT JOIN ot o ON o.id_ot = r.id_ot " +
                "LEFT JOIN transferencias t ON t.id_transferencia = r.id_transferencia " +
                "LEFT JOIN almacenes ao ON ao.id_almacen = t.id_almacen_origen LEFT JOIN almacenes ad ON ad.id_almacen = t.id_almacen_destino " +
                "LEFT JOIN usuarios ue ON ue.id_usuario = r.id_usuario_emite " +
                "LEFT JOIN solicitudes_activos sa ON sa.id_solicitud = r.id_solicitud_activo " +
                "WHERE (? = '' OR r.tipo_remision = ? OR (? = 'INSUMOS' AND r.tipo_remision IN ('SALIDA_INSUMOS', 'TRANSFERENCIA'))) " +
                "AND (? = '' OR (? = 'VIGENTE' AND r.anulada = false) OR (? = 'ANULADA' AND r.anulada = true)) " +
                "AND " + condRemision(permisos) + " ORDER BY r.fecha_hora DESC NULLS LAST, r.id_remision DESC");
            ps.setString(1, tr);
            ps.setString(2, tr);
            ps.setString(3, tr);
            ps.setString(4, er);
            ps.setString(5, er);
            ps.setString(6, er);
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                String t2 = rs.getString("tipo_remision");
                o.put("id", rs.getInt("id_remision"));
                o.put("numero", txt(rs.getString("numero_remision")));
                o.put("tipo", txt(t2));
                o.put("fecha", fechaHora(rs.getTimestamp("fecha_hora")));
                o.put("anulada", rs.getBoolean("anulada"));
                o.put("motivo_anulacion", txt(rs.getString("motivo_anulacion")));
                o.put("fecha_anulacion", fechaHora(rs.getTimestamp("fecha_anulacion")));
                o.put("transportista", txt(rs.getString("transportista")));
                o.put("ci", txt(rs.getString("transportista_ci")));
                o.put("referencia", "SALIDA_ACTIVOS".equals(t2)
                        ? "Activos " + txt(rs.getString("numero_sa")) + ("REPARACION".equals(rs.getString("tipo_sa")) ? " (reparación)" : "ENTREGA_OT".equals(rs.getString("tipo_sa")) ? " (" + txt(rs.getString("numero_ot")) + ")" : " (transferencia)")
                        : "TRANSFERENCIA".equals(t2)
                        ? txt(rs.getString("numero_tr")) + ": " + txt(rs.getString("origen")) + " -> " + txt(rs.getString("destino"))
                        : txt(rs.getString("numero_ot")) + (rs.getString("titulo") == null ? "" : " - " + rs.getString("titulo")));
                o.put("emitio", txt(rs.getString("emitio")));
                o.put("items", rs.getInt("items"));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("anularRemision".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idRem = numero(request.getParameter("id"));
                String motivo = limpio(request.getParameter("motivo"));
                if (motivo.isEmpty()) {
                    throw new IllegalArgumentException("Indique el motivo de la anulación");
                }
                ps = conn.prepareStatement("SELECT tipo_remision, anulada, id_ot, id_transferencia, numero_remision FROM remisiones WHERE id_remision = ? FOR UPDATE");
                ps.setInt(1, idRem);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("Remisión no encontrada");
                }
                String tipoRem = rs.getString(1);
                boolean anulada = rs.getBoolean(2);
                int idOT = rs.getInt(3);
                int idTr = rs.getInt(4);
                String numeroRem = rs.getString(5);
                rs.close(); ps.close(); rs = null; ps = null;
                if (anulada) {
                    throw new IllegalArgumentException("La remisión ya está anulada");
                }
                if ("SALIDA_INSUMOS".equals(tipoRem)) {
                    if (!existe(conn, "SELECT 1 FROM ot WHERE id_ot = ? AND " + permisos.sqlCentros("id_centro"), idOT)) {
                        throw new IllegalArgumentException("La remisión es de una OT de un centro al que no tiene acceso");
                    }
                    ps = conn.prepareStatement("SELECT estado FROM ot WHERE id_ot = ? FOR UPDATE");
                    ps.setInt(1, idOT);
                    rs = ps.executeQuery();
                    String estadoOT = rs.next() ? rs.getString(1) : "";
                    rs.close(); ps.close(); rs = null; ps = null;
                    if ("FINALIZADA".equals(estadoOT)) {
                        throw new IllegalArgumentException("La OT está finalizada. Anule primero la finalización en \"Finalizar OT\" y luego la remisión.");
                    }
                    marcarRemisionAnulada(conn, idRem, idUsuario, motivo);
                    // Los insumos vuelven a quedar pendientes de salida (el stock no cambia: ya se descontó al suministrar)
                    ps = conn.prepareStatement("UPDATE detalle_ot SET id_remision = NULL WHERE id_remision = ?");
                    ps.setInt(1, idRem);
                    ps.executeUpdate();
                    ps.close(); ps = null;
                    eventoOT(conn, idOT, "ANULACION_REMISION", idUsuario, "Remisión " + numeroRem + " anulada: " + motivo);
                } else if ("TRANSFERENCIA".equals(tipoRem)) {
                    exigirOrigenTransferencia(conn, permisos, idTr);
                    anularTransferencia(conn, idTr, idUsuario, motivo);
                } else {
                    throw new IllegalArgumentException("Las remisiones de salida de activos se anulan en Activos Fijos -> Aprobaciones (pestaña Historial)");
                }
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

        // =================================================================
        // AJUSTE PUNTUAL
        // =================================================================
        else if ("listarAjustes".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT a.id_ajuste, a.numero, a.fecha, a.tipo, a.origen, a.motivo, a.anulado, a.fecha_anulacion, a.motivo_anulacion, " +
                "       c.nombre AS centro, cc.codigo, cc.descripcion AS cc_desc, al.nombre AS almacen, u.nombre || ' ' || u.apellido AS usuario, " +
                "       (SELECT COUNT(*) FROM ajustes_detalle d WHERE d.id_ajuste = a.id_ajuste) AS items, " +
                "       (SELECT COALESCE(SUM(d.cantidad * COALESCE(d.costo_unitario, 0)), 0) FROM ajustes_detalle d WHERE d.id_ajuste = a.id_ajuste) AS valor " +
                "FROM ajustes a LEFT JOIN centros c ON c.id_centro = a.id_centro " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = a.id_centro_costo " +
                "LEFT JOIN almacenes al ON al.id_almacen = a.id_almacen LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario " +
                "WHERE " + permisos.sqlCentros("a.id_centro") + " ORDER BY a.fecha DESC, a.id_ajuste DESC");
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_ajuste"));
                o.put("numero", txt(rs.getString("numero")));
                o.put("fecha", fechaHora(rs.getTimestamp("fecha")));
                o.put("tipo", txt(rs.getString("tipo")));
                o.put("origen", txt(rs.getString("origen")));
                o.put("motivo", txt(rs.getString("motivo")));
                o.put("anulado", rs.getBoolean("anulado"));
                o.put("fecha_anulacion", fechaHora(rs.getTimestamp("fecha_anulacion")));
                o.put("motivo_anulacion", txt(rs.getString("motivo_anulacion")));
                o.put("centro", txt(rs.getString("centro")));
                o.put("centro_costo", nombreCC(rs.getString("codigo"), rs.getString("cc_desc")));
                o.put("almacen", txt(rs.getString("almacen")));
                o.put("usuario", txt(rs.getString("usuario")));
                o.put("items", rs.getInt("items"));
                o.put("valor", rs.getBigDecimal("valor").toPlainString());
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("detalleAjuste".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT i.codigo, i.descripcion, u.abreviatura, d.cantidad, d.costo_unitario FROM ajustes_detalle d " +
                "JOIN insumos i ON i.id_insumo = d.id_insumo LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida " +
                "WHERE d.id_ajuste = ? ORDER BY d.id_detalle");
            ps.setInt(1, numero(request.getParameter("id")));
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("codigo", txt(rs.getString(1)));
                o.put("nombre", txt(rs.getString(2)));
                o.put("unidad", txt(rs.getString(3)));
                o.put("cantidad", rs.getInt(4));
                o.put("costo", rs.getBigDecimal(5) == null ? "0" : rs.getBigDecimal(5).toPlainString());
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("crearAjuste".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                String tipoAjuste = request.getParameter("tipo_ajuste");
                if (!"CONSUMO".equals(tipoAjuste) && !"ROTURA_PERDIDA".equals(tipoAjuste)
                        && !"VENCIMIENTO".equals(tipoAjuste) && !"CORRECCION_CONTEO".equals(tipoAjuste)) {
                    throw new IllegalArgumentException("Seleccione el tipo de ajuste");
                }
                int idCentro = numero(request.getParameter("id_centro"));
                int idCC = numero(request.getParameter("id_centro_costo"));
                int idAlmacen = numero(request.getParameter("id_almacen"));
                String motivo = limpio(request.getParameter("motivo"));
                if (idCentro == 0) {
                    throw new IllegalArgumentException("Seleccione el centro");
                }
                permisos.exigirCentro(idCentro);
                if (idCC == 0 && !"CORRECCION_CONTEO".equals(tipoAjuste)) {
                    throw new IllegalArgumentException("Seleccione el centro de costo");
                }
                if (idAlmacen == 0) {
                    throw new IllegalArgumentException("Seleccione el almacén");
                }
                if (motivo.isEmpty()) {
                    throw new IllegalArgumentException("Indique el motivo del ajuste");
                }
                if (idCC != 0 && !existe(conn, "SELECT 1 FROM centros_costo WHERE id_centro_costo = ? AND id_centro = ?", idCC, idCentro)) {
                    throw new IllegalArgumentException("El centro de costo no pertenece al centro seleccionado");
                }
                if (!existe(conn, "SELECT 1 FROM almacenes WHERE id_almacen = ? AND id_centro = ? AND activo = true", idAlmacen, idCentro)) {
                    throw new IllegalArgumentException("El almacén no pertenece al centro seleccionado");
                }
                JSONArray items = new JSONArray(request.getParameter("items"));
                if (items.length() == 0) {
                    throw new IllegalArgumentException("Agregue al menos un insumo");
                }
                String numeroAj = siguiente(conn, "SELECT 'AJ-' || lpad(nextval('public.seq_ajuste')::text, 6, '0')");
                int idAjuste = crearAjuste(conn, numeroAj, tipoAjuste, "MANUAL", idCentro, idCC, idAlmacen, motivo, idUsuario, items);
                conn.commit();
                resp.put("ok", true);
                resp.put("numero", numeroAj);
                resp.put("id", idAjuste);
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
        else if ("anularAjuste".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idAjuste = numero(request.getParameter("id"));
                String motivo = limpio(request.getParameter("motivo"));
                if (motivo.isEmpty()) {
                    throw new IllegalArgumentException("Indique el motivo de la anulación");
                }
                ps = conn.prepareStatement("SELECT numero, anulado, id_almacen, id_centro_costo FROM ajustes WHERE id_ajuste = ? FOR UPDATE");
                ps.setInt(1, idAjuste);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("Ajuste no encontrado");
                }
                String numeroAj = rs.getString(1);
                boolean anulado = rs.getBoolean(2);
                int idAlmacen = rs.getInt(3);
                if (!existe(conn, "SELECT 1 FROM almacenes WHERE id_almacen = ? AND " + permisos.sqlCentros("id_centro"), idAlmacen)) {
                    throw new IllegalArgumentException("El ajuste es de un centro al que no tiene acceso");
                }
                int cc = rs.getInt(4);
                Integer idCC = rs.wasNull() ? null : Integer.valueOf(cc);
                rs.close(); ps.close(); rs = null; ps = null;
                if (anulado) {
                    throw new IllegalArgumentException("El ajuste ya está anulado");
                }
                ps = conn.prepareStatement("SELECT id_insumo, cantidad, costo_unitario FROM ajustes_detalle WHERE id_ajuste = ?");
                ps.setInt(1, idAjuste);
                rs = ps.executeQuery();
                java.util.List<int[]> lineas = new java.util.ArrayList<int[]>();
                java.util.List<BigDecimal> costos = new java.util.ArrayList<BigDecimal>();
                while (rs.next()) {
                    lineas.add(new int[]{rs.getInt(1), rs.getInt(2)});
                    costos.add(rs.getBigDecimal(3));
                }
                rs.close(); ps.close(); rs = null; ps = null;
                for (int i = 0; i < lineas.size(); i++) {
                    int[] ln = lineas.get(i);
                    MovimientoStock m = new MovimientoStock(MovimientoStock.ANULACION_AJUSTE, ln[0], idAlmacen, -ln[1], idUsuario);
                    m.documento = numeroAj;
                    m.idAjuste = Integer.valueOf(idAjuste);
                    m.idCentroCosto = idCC;
                    m.costoUnitario = costos.get(i);
                    m.observacion = "Anulación: " + motivo;
                    m.registrar(conn);
                }
                ps = conn.prepareStatement("UPDATE ajustes SET anulado = true, fecha_anulacion = CURRENT_TIMESTAMP, id_usuario_anula = ?, motivo_anulacion = ? WHERE id_ajuste = ?");
                ps.setInt(1, idUsuario);
                ps.setString(2, motivo);
                ps.setInt(3, idAjuste);
                ps.executeUpdate();
                ps.close(); ps = null;
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

        // =================================================================
        // TRANSFERENCIAS ENTRE ALMACENES
        // =================================================================
        else if ("listarTransferencias".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT t.id_transferencia, t.numero, t.fecha, t.observacion, t.anulada, t.fecha_anulacion, t.motivo_anulacion, " +
                "       ao.nombre AS origen, co.nombre AS centro_origen, ad.nombre AS destino, cd.nombre AS centro_destino, " +
                "       u.nombre || ' ' || u.apellido AS usuario, r.id_remision, r.numero_remision, " +
                "       (SELECT COUNT(*) FROM transferencias_detalle d WHERE d.id_transferencia = t.id_transferencia) AS items " +
                "FROM transferencias t JOIN almacenes ao ON ao.id_almacen = t.id_almacen_origen LEFT JOIN centros co ON co.id_centro = ao.id_centro " +
                "JOIN almacenes ad ON ad.id_almacen = t.id_almacen_destino LEFT JOIN centros cd ON cd.id_centro = ad.id_centro " +
                "LEFT JOIN usuarios u ON u.id_usuario = t.id_usuario LEFT JOIN remisiones r ON r.id_transferencia = t.id_transferencia " +
                "WHERE (" + permisos.sqlCentros("ao.id_centro") + " OR " + permisos.sqlCentros("ad.id_centro") + ") " +
                "ORDER BY t.fecha DESC, t.id_transferencia DESC");
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_transferencia"));
                o.put("numero", txt(rs.getString("numero")));
                o.put("fecha", fechaHora(rs.getTimestamp("fecha")));
                o.put("observacion", txt(rs.getString("observacion")));
                o.put("anulada", rs.getBoolean("anulada"));
                o.put("fecha_anulacion", fechaHora(rs.getTimestamp("fecha_anulacion")));
                o.put("motivo_anulacion", txt(rs.getString("motivo_anulacion")));
                o.put("origen", txt(rs.getString("origen")) + " (" + txt(rs.getString("centro_origen")) + ")");
                o.put("destino", txt(rs.getString("destino")) + " (" + txt(rs.getString("centro_destino")) + ")");
                o.put("usuario", txt(rs.getString("usuario")));
                o.put("id_remision", rs.getInt("id_remision"));
                o.put("remision", txt(rs.getString("numero_remision")));
                o.put("items", rs.getInt("items"));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("detalleTransferencia".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT i.codigo, i.descripcion, u.abreviatura, d.cantidad FROM transferencias_detalle d JOIN insumos i ON i.id_insumo = d.id_insumo " +
                "LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida WHERE d.id_transferencia = ? ORDER BY d.id_detalle");
            ps.setInt(1, numero(request.getParameter("id")));
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("codigo", txt(rs.getString(1)));
                o.put("nombre", txt(rs.getString(2)));
                o.put("unidad", txt(rs.getString(3)));
                o.put("cantidad", rs.getInt(4));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("crearTransferencia".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int origen = numero(request.getParameter("id_almacen_origen"));
                int destino = numero(request.getParameter("id_almacen_destino"));
                if (origen == 0 || destino == 0) {
                    throw new IllegalArgumentException("Seleccione el almacén de origen y el de destino");
                }
                if (!existe(conn, "SELECT 1 FROM almacenes WHERE id_almacen = ? AND " + permisos.sqlCentros("id_centro"), origen)) {
                    throw new IllegalArgumentException("El almacén de origen no pertenece a sus centros");
                }
                if (origen == destino) {
                    throw new IllegalArgumentException("El almacén de destino debe ser distinto al de origen");
                }
                if (!existe(conn, "SELECT 1 FROM almacenes WHERE id_almacen = ? AND activo = true", destino)) {
                    throw new IllegalArgumentException("El almacén de destino no existe o está inactivo");
                }
                Transportista tr = transportista(request, false);
                JSONArray items = new JSONArray(request.getParameter("items"));
                if (items.length() == 0) {
                    throw new IllegalArgumentException("Agregue al menos un insumo");
                }
                String observacion = limpio(request.getParameter("observacion"));
                String numeroTr = siguiente(conn, "SELECT 'TR-' || lpad(nextval('public.seq_transferencia')::text, 6, '0')");
                ps = conn.prepareStatement(
                    "INSERT INTO transferencias (numero, id_almacen_origen, id_almacen_destino, observacion, id_usuario) VALUES (?, ?, ?, ?, ?) RETURNING id_transferencia");
                ps.setString(1, numeroTr);
                ps.setInt(2, origen);
                ps.setInt(3, destino);
                if (observacion.isEmpty()) { ps.setNull(4, Types.VARCHAR); } else { ps.setString(4, observacion); }
                ps.setInt(5, idUsuario);
                rs = ps.executeQuery();
                rs.next();
                int idTr = rs.getInt(1);
                rs.close(); ps.close(); rs = null; ps = null;

                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    int idInsumo = it.optInt("id_insumo", 0);
                    int cantidad = it.optInt("cantidad", 0);
                    if (idInsumo <= 0 || cantidad <= 0) {
                        throw new IllegalArgumentException("Cada línea debe tener insumo y una cantidad mayor a 0");
                    }
                    MovimientoStock sale = new MovimientoStock(MovimientoStock.TRANSFERENCIA_SALIDA, idInsumo, origen, -cantidad, idUsuario);
                    sale.documento = numeroTr;
                    sale.idTransferencia = Integer.valueOf(idTr);
                    sale.observacion = "Transferencia a otro almacén";
                    sale.registrar(conn);
                    MovimientoStock entra = new MovimientoStock(MovimientoStock.TRANSFERENCIA_ENTRADA, idInsumo, destino, cantidad, idUsuario);
                    entra.documento = numeroTr;
                    entra.idTransferencia = Integer.valueOf(idTr);
                    entra.observacion = "Transferencia desde otro almacén";
                    entra.registrar(conn);
                    ps = conn.prepareStatement("INSERT INTO transferencias_detalle (id_transferencia, id_insumo, cantidad) VALUES (?, ?, ?)");
                    ps.setInt(1, idTr);
                    ps.setInt(2, idInsumo);
                    ps.setInt(3, cantidad);
                    ps.executeUpdate();
                    ps.close(); ps = null;
                }
                String numeroRem = siguiente(conn, "SELECT 'REM-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad(nextval('public.seq_remision')::text, 6, '0')");
                int idRem = insertarRemision(conn, numeroRem, "TRANSFERENCIA", null, Integer.valueOf(idTr), idUsuario, tr, observacion);
                Remisiones.copiarDetalle(conn, idRem); // detalle propio de la remisión (remisiones_detalle)
                conn.commit();
                resp.put("ok", true);
                resp.put("numero", numeroTr);
                resp.put("remision", numeroRem);
                resp.put("id_remision", idRem);
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
        else if ("anularTransferencia".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                String motivo = limpio(request.getParameter("motivo"));
                if (motivo.isEmpty()) {
                    throw new IllegalArgumentException("Indique el motivo de la anulación");
                }
                exigirOrigenTransferencia(conn, permisos, numero(request.getParameter("id")));
                anularTransferencia(conn, numero(request.getParameter("id")), idUsuario, motivo);
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

        // =================================================================
        // INVENTARIO FÍSICO (conteo) -> ajuste por diferencias
        // =================================================================
        else if ("registrarConteo".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idAlmacen = numero(request.getParameter("id_almacen"));
                String observacion = limpio(request.getParameter("observacion"));
                ps = conn.prepareStatement("SELECT id_centro FROM almacenes WHERE id_almacen = ? AND activo = true");
                ps.setInt(1, idAlmacen);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("Seleccione un almacén activo");
                }
                int idCentro = rs.getInt(1);
                permisos.exigirCentro(idCentro);
                rs.close(); ps.close(); rs = null; ps = null;
                JSONArray conteo = new JSONArray(request.getParameter("items"));
                JSONArray diferencias = new JSONArray();
                for (int i = 0; i < conteo.length(); i++) {
                    JSONObject c = conteo.getJSONObject(i);
                    int idInsumo = c.optInt("id_insumo", 0);
                    int contado = c.optInt("contado", -1);
                    if (idInsumo <= 0 || contado < 0) {
                        throw new IllegalArgumentException("Las cantidades contadas deben ser números enteros mayores o iguales a 0");
                    }
                    ps = conn.prepareStatement("SELECT stock_actual FROM stock_almacen WHERE id_insumo = ? AND id_almacen = ? FOR UPDATE");
                    ps.setInt(1, idInsumo);
                    ps.setInt(2, idAlmacen);
                    rs = ps.executeQuery();
                    int sistema = rs.next() ? rs.getInt(1) : 0;
                    rs.close(); ps.close(); rs = null; ps = null;
                    if (contado != sistema) {
                        JSONObject d = new JSONObject();
                        d.put("id_insumo", idInsumo);
                        d.put("cantidad", contado - sistema);
                        diferencias.put(d);
                    }
                }
                if (diferencias.length() == 0) {
                    conn.rollback();
                    resp.put("ok", true);
                    resp.put("sin_diferencias", true);
                } else {
                    String numeroAj = siguiente(conn, "SELECT 'AJ-' || lpad(nextval('public.seq_ajuste')::text, 6, '0')");
                    crearAjuste(conn, numeroAj, "CORRECCION_CONTEO", "INVENTARIO_FISICO", idCentro, 0, idAlmacen,
                            "Inventario físico" + (observacion.isEmpty() ? "" : ": " + observacion), idUsuario, diferencias);
                    conn.commit();
                    resp.put("ok", true);
                    resp.put("numero", numeroAj);
                    resp.put("diferencias", diferencias.length());
                    resp.put("fecha", fechaActual(conn));
                }
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // =================================================================
        // KARDEX
        // =================================================================
        else if ("kardex".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT m.fecha, m.tipo, m.documento, m.cantidad, m.saldo, m.costo_unitario, m.observacion, a.nombre AS almacen, " +
                "       u.nombre || ' ' || u.apellido AS usuario, cc.codigo AS cc " +
                "FROM movimientos_stock m JOIN almacenes a ON a.id_almacen = m.id_almacen " +
                "LEFT JOIN usuarios u ON u.id_usuario = m.id_usuario LEFT JOIN centros_costo cc ON cc.id_centro_costo = m.id_centro_costo " +
                "WHERE m.id_insumo = ? AND (? = 0 OR m.id_almacen = ?) AND " + permisos.sqlCentros("a.id_centro") + " AND " + SQL_RANGO_FECHAS.replace("{c}", "m.fecha") +
                " ORDER BY m.fecha, m.id_mov");
            int idInsumo = numero(request.getParameter("id_insumo"));
            int idAlmacen = numero(request.getParameter("id_almacen"));
            ps.setInt(1, idInsumo);
            ps.setInt(2, idAlmacen);
            ps.setInt(3, idAlmacen);
            ps.setString(4, fechaParam(request.getParameter("desde")));
            ps.setString(5, fechaParam(request.getParameter("hasta")));
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("fecha", fechaHora(rs.getTimestamp("fecha")));
                o.put("tipo", txt(rs.getString("tipo")));
                o.put("documento", txt(rs.getString("documento")));
                o.put("cantidad", rs.getInt("cantidad"));
                o.put("saldo", rs.getInt("saldo"));
                o.put("costo", rs.getBigDecimal("costo_unitario") == null ? "" : rs.getBigDecimal("costo_unitario").toPlainString());
                o.put("observacion", txt(rs.getString("observacion")));
                o.put("almacen", txt(rs.getString("almacen")));
                o.put("usuario", txt(rs.getString("usuario")));
                o.put("centro_costo", txt(rs.getString("cc")));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }

        // =================================================================
        // REPORTES Y ALERTAS
        // =================================================================
        // Consumo por centro de costo: insumos entregados en OT + ajustes de consumo no anulados
        else if ("reporteConsumo".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT c.nombre AS centro, cc.codigo, cc.descripcion, SUM(x.cantidad) AS unidades, SUM(x.valor) AS valor, COUNT(DISTINCT x.doc) AS documentos " +
                "FROM (" + SQL_CONSUMOS + ") x JOIN centros_costo cc ON cc.id_centro_costo = x.id_cc LEFT JOIN centros c ON c.id_centro = cc.id_centro " +
                "WHERE " + SQL_RANGO_FECHAS.replace("{c}", "x.fecha") +
                " AND " + permisos.sqlCentros("cc.id_centro") + " GROUP BY c.nombre, cc.codigo, cc.descripcion ORDER BY SUM(x.valor) DESC");
            ps.setString(1, fechaParam(request.getParameter("desde")));
            ps.setString(2, fechaParam(request.getParameter("hasta")));
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("centro", txt(rs.getString("centro")));
                o.put("centro_costo", nombreCC(rs.getString("codigo"), rs.getString("descripcion")));
                o.put("unidades", rs.getLong("unidades"));
                o.put("valor", rs.getBigDecimal("valor") == null ? "0" : rs.getBigDecimal("valor").toPlainString());
                o.put("documentos", rs.getInt("documentos"));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("reporteMasUsados".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT i.codigo, i.descripcion, u.abreviatura, SUM(x.cantidad) AS unidades, SUM(x.valor) AS valor " +
                "FROM (" + SQL_CONSUMOS + ") x JOIN insumos i ON i.id_insumo = x.id_insumo LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida " +
                "LEFT JOIN centros_costo ccx ON ccx.id_centro_costo = x.id_cc " +
                "WHERE " + SQL_RANGO_FECHAS.replace("{c}", "x.fecha") +
                " AND " + permisos.sqlCentros("ccx.id_centro") + " GROUP BY i.codigo, i.descripcion, u.abreviatura ORDER BY SUM(x.cantidad) DESC LIMIT 20");
            ps.setString(1, fechaParam(request.getParameter("desde")));
            ps.setString(2, fechaParam(request.getParameter("hasta")));
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("codigo", txt(rs.getString(1)));
                o.put("nombre", txt(rs.getString(2)));
                o.put("unidad", txt(rs.getString(3)));
                o.put("unidades", rs.getLong(4));
                o.put("valor", rs.getBigDecimal(5) == null ? "0" : rs.getBigDecimal(5).toPlainString());
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("reporteValorizacion".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT c.nombre AS centro, a.nombre AS almacen, COUNT(*) FILTER (WHERE s.stock_actual > 0) AS insumos, " +
                "       COALESCE(SUM(s.stock_actual), 0) AS unidades, COALESCE(SUM(s.stock_actual * COALESCE(i.precio_compra, 0)), 0) AS valor " +
                "FROM stock_almacen s JOIN almacenes a ON a.id_almacen = s.id_almacen JOIN insumos i ON i.id_insumo = s.id_insumo " +
                "LEFT JOIN centros c ON c.id_centro = a.id_centro WHERE " + permisos.sqlCentros("a.id_centro") + " GROUP BY c.nombre, a.nombre ORDER BY c.nombre, a.nombre");
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("centro", txt(rs.getString(1)));
                o.put("almacen", txt(rs.getString(2)));
                o.put("insumos", rs.getInt(3));
                o.put("unidades", rs.getLong(4));
                o.put("valor", rs.getBigDecimal(5).toPlainString());
                lista.put(o);
            }
            responderJson(response, out, lista);
        }
        else if ("alertasMinimo".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT i.codigo, i.descripcion, a.nombre AS almacen, s.stock_actual, s.stock_minimo " +
                "FROM stock_almacen s JOIN insumos i ON i.id_insumo = s.id_insumo JOIN almacenes a ON a.id_almacen = s.id_almacen " +
                "WHERE s.stock_minimo > 0 AND s.stock_actual <= s.stock_minimo AND i.activo = true AND " + permisos.sqlCentros("a.id_centro") + " " +
                "ORDER BY (s.stock_actual::numeric / s.stock_minimo), i.descripcion LIMIT 50");
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("codigo", txt(rs.getString(1)));
                o.put("nombre", txt(rs.getString(2)));
                o.put("almacen", txt(rs.getString(3)));
                o.put("stock", rs.getInt(4));
                o.put("minimo", rs.getInt(5));
                lista.put(o);
            }
            responderJson(response, out, lista);
        }

        // =================================================================
        // IMPRESIÓN (JRXML): remisión y Kardex
        // =================================================================
        /* (impresión en PDF: solo en la web) */
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


    /** Insumos de una OT sin salida (sin remisión vigente). */
    private static final String SQL_PENDIENTES_SALIDA =
        "SELECT 1 FROM detalle_ot d LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false " +
        "WHERE d.id_ot = ? AND r.id_remision IS NULL";

    /** Rango de fechas opcional ('' = sin límite). Usa 2 parámetros: desde, hasta (yyyy-MM-dd). */
    private static final String SQL_RANGO_FECHAS =
        "{c} >= COALESCE(CAST(NULLIF(?, '') AS date), '-infinity'::date) " +
        "AND {c} < COALESCE(CAST(NULLIF(?, '') AS date) + 1, 'infinity'::date)";

    /** Consumos: insumos entregados en OT (a su centro de costo) + ajustes de consumo no anulados. */
    private static final String SQL_CONSUMOS =
        "SELECT d.fecha_suministro AS fecha, o.id_centro_costo AS id_cc, d.id_insumo, d.cantidad, d.cantidad * d.precio_unitario AS valor, o.numero_ot AS doc " +
        "  FROM detalle_ot d JOIN ot o ON o.id_ot = d.id_ot " +
        "UNION ALL " +
        "SELECT a.fecha, a.id_centro_costo, x.id_insumo, -x.cantidad, -x.cantidad * COALESCE(x.costo_unitario, 0), a.numero " +
        "  FROM ajustes a JOIN ajustes_detalle x ON x.id_ajuste = a.id_ajuste " +
        " WHERE a.tipo = 'CONSUMO' AND a.anulado = false AND a.id_centro_costo IS NOT NULL";

    /** Condición SQL: remisiones visibles para el usuario (requiere alias r, o, ao, ad). */
    private static String condRemision(Permisos p) {
        if (p.todosLosCentros()) {
            return "TRUE";
        }
        return "(" + p.sqlCentros("o.id_centro") + " OR " + p.sqlCentros("ao.id_centro") + " OR " + p.sqlCentros("ad.id_centro")
                + " OR EXISTS (SELECT 1 FROM solicitudes_activos sa WHERE sa.id_solicitud = r.id_solicitud_activo AND " + p.sqlCentros("sa.id_centro_destino") + ")"
                + " OR EXISTS (SELECT 1 FROM solicitudes_activos_detalle sd WHERE sd.id_solicitud = r.id_solicitud_activo AND " + p.sqlCentros("sd.id_centro_origen") + "))";
    }

    /** La transferencia solo la anula quien opera el centro del almacén de origen. */
    private static void exigirOrigenTransferencia(Connection conn, Permisos p, int idTr) throws SQLException {
        if (!existe(conn, "SELECT 1 FROM transferencias t JOIN almacenes a ON a.id_almacen = t.id_almacen_origen WHERE t.id_transferencia = ? AND "
                + p.sqlCentros("a.id_centro"), idTr)) {
            throw new IllegalArgumentException("La transferencia sale de un centro al que no tiene acceso");
        }
    }

    /** Datos del transportista (nombre, apellido y CI obligatorios si obligatorio=true; vehículo opcional). */
    private static class Transportista {
        String nombre, apellido, ci, marca, modelo, chapa;
    }

    private static Transportista transportista(HttpServletRequest req, boolean obligatorio) {
        Transportista t = new Transportista();
        t.nombre = limpio(req.getParameter("transportista_nombre"));
        t.apellido = limpio(req.getParameter("transportista_apellido"));
        t.ci = limpio(req.getParameter("transportista_ci"));
        t.marca = limpio(req.getParameter("vehiculo_marca"));
        t.modelo = limpio(req.getParameter("vehiculo_modelo"));
        t.chapa = limpio(req.getParameter("vehiculo_chapa"));
        if (obligatorio && (t.nombre.isEmpty() || t.apellido.isEmpty() || t.ci.isEmpty())) {
            throw new IllegalArgumentException("Complete nombre, apellido y cédula de identidad de quien transporta");
        }
        if (t.nombre.length() > 100 || t.apellido.length() > 100 || t.ci.length() > 20
                || t.marca.length() > 50 || t.modelo.length() > 50 || t.chapa.length() > 20) {
            throw new IllegalArgumentException("Algún dato del transportista o del vehículo es demasiado largo");
        }
        return t;
    }

    private static int insertarRemision(Connection conn, String numero, String tipoRem, Integer idOT, Integer idTr,
                                        int idUsuario, Transportista t, String observacion) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
            "INSERT INTO remisiones (numero_remision, fecha_emision, fecha_hora, tipo_remision, id_ot, id_transferencia, id_usuario_emite, " +
            "transportista_nombre, transportista_apellido, transportista_ci, vehiculo_marca, vehiculo_modelo, vehiculo_chapa, observacion, anulada) " +
            "VALUES (?, CURRENT_DATE, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false) RETURNING id_remision");
        try {
            p.setString(1, numero);
            p.setString(2, tipoRem);
            if (idOT == null) { p.setNull(3, Types.INTEGER); } else { p.setInt(3, idOT.intValue()); }
            if (idTr == null) { p.setNull(4, Types.INTEGER); } else { p.setInt(4, idTr.intValue()); }
            p.setInt(5, idUsuario);
            String[] v = {t.nombre, t.apellido, t.ci, t.marca, t.modelo, t.chapa, observacion};
            for (int i = 0; i < v.length; i++) {
                if (v[i] == null || v[i].isEmpty()) { p.setNull(6 + i, Types.VARCHAR); } else { p.setString(6 + i, v[i]); }
            }
            ResultSet r = p.executeQuery();
            r.next();
            int id = r.getInt(1);
            r.close();
            return id;
        } finally {
            p.close();
        }
    }

    private static void marcarRemisionAnulada(Connection conn, int idRem, int idUsuario, String motivo) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
            "UPDATE remisiones SET anulada = true, fecha_anulacion = CURRENT_TIMESTAMP, id_usuario_anula = ?, motivo_anulacion = ? WHERE id_remision = ?");
        try {
            p.setInt(1, idUsuario);
            p.setString(2, motivo);
            p.setInt(3, idRem);
            p.executeUpdate();
        } finally {
            p.close();
        }
    }

    /** Crea un ajuste con sus líneas y mueve el stock. items: [{id_insumo, cantidad}]
     *  CORRECCION_CONTEO: la cantidad lleva signo (+ entra, - sale); el resto siempre sale del stock. */
    private static int crearAjuste(Connection conn, String numero, String tipoAjuste, String origen, int idCentro, int idCC,
                                   int idAlmacen, String motivo, int idUsuario, JSONArray items) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
            "INSERT INTO ajustes (numero, tipo, origen, id_centro, id_centro_costo, id_almacen, motivo, id_usuario) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_ajuste");
        int idAjuste;
        try {
            p.setString(1, numero);
            p.setString(2, tipoAjuste);
            p.setString(3, origen);
            p.setInt(4, idCentro);
            if (idCC == 0) { p.setNull(5, Types.INTEGER); } else { p.setInt(5, idCC); }
            p.setInt(6, idAlmacen);
            p.setString(7, motivo);
            p.setInt(8, idUsuario);
            ResultSet r = p.executeQuery();
            r.next();
            idAjuste = r.getInt(1);
            r.close();
        } finally {
            p.close();
        }
        boolean conSigno = "CORRECCION_CONTEO".equals(tipoAjuste);
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            int idInsumo = it.optInt("id_insumo", 0);
            int cantidad = it.optInt("cantidad", 0);
            if (idInsumo <= 0 || cantidad == 0 || (!conSigno && cantidad < 0)) {
                throw new IllegalArgumentException(conSigno ? "Cada línea debe tener insumo y una cantidad distinta de 0"
                                                            : "Cada línea debe tener insumo y una cantidad mayor a 0");
            }
            int movimiento = conSigno ? cantidad : -cantidad;
            BigDecimal costo = precioInsumo(conn, idInsumo);
            MovimientoStock m = new MovimientoStock(MovimientoStock.AJUSTE, idInsumo, idAlmacen, movimiento, idUsuario);
            m.documento = numero;
            m.idAjuste = Integer.valueOf(idAjuste);
            m.idCentroCosto = idCC == 0 ? null : Integer.valueOf(idCC);
            m.costoUnitario = costo;
            m.observacion = tipoAjuste + ": " + motivo;
            m.registrar(conn);
            PreparedStatement d = conn.prepareStatement("INSERT INTO ajustes_detalle (id_ajuste, id_insumo, cantidad, costo_unitario) VALUES (?, ?, ?, ?)");
            try {
                d.setInt(1, idAjuste);
                d.setInt(2, idInsumo);
                d.setInt(3, movimiento);
                d.setBigDecimal(4, costo);
                d.executeUpdate();
            } finally {
                d.close();
            }
        }
        return idAjuste;
    }

    /** Revierte una transferencia (el stock vuelve al origen) y anula su remisión. */
    private static void anularTransferencia(Connection conn, int idTr, int idUsuario, String motivo) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT numero, anulada, id_almacen_origen, id_almacen_destino FROM transferencias WHERE id_transferencia = ? FOR UPDATE");
        String numero;
        int origen, destino;
        try {
            p.setInt(1, idTr);
            ResultSet r = p.executeQuery();
            if (!r.next()) {
                throw new IllegalArgumentException("Transferencia no encontrada");
            }
            if (r.getBoolean(2)) {
                throw new IllegalArgumentException("La transferencia ya está anulada");
            }
            numero = r.getString(1);
            origen = r.getInt(3);
            destino = r.getInt(4);
            r.close();
        } finally {
            p.close();
        }
        p = conn.prepareStatement("SELECT id_insumo, cantidad FROM transferencias_detalle WHERE id_transferencia = ?");
        java.util.List<int[]> lineas = new java.util.ArrayList<int[]>();
        try {
            p.setInt(1, idTr);
            ResultSet r = p.executeQuery();
            while (r.next()) {
                lineas.add(new int[]{r.getInt(1), r.getInt(2)});
            }
            r.close();
        } finally {
            p.close();
        }
        for (int[] ln : lineas) {
            MovimientoStock sale = new MovimientoStock(MovimientoStock.ANULACION_TRANSFERENCIA, ln[0], destino, -ln[1], idUsuario);
            sale.documento = numero;
            sale.idTransferencia = Integer.valueOf(idTr);
            sale.observacion = "Anulación: " + motivo;
            sale.registrar(conn);
            MovimientoStock vuelve = new MovimientoStock(MovimientoStock.ANULACION_TRANSFERENCIA, ln[0], origen, ln[1], idUsuario);
            vuelve.documento = numero;
            vuelve.idTransferencia = Integer.valueOf(idTr);
            vuelve.observacion = "Anulación: " + motivo;
            vuelve.registrar(conn);
        }
        p = conn.prepareStatement("UPDATE transferencias SET anulada = true, fecha_anulacion = CURRENT_TIMESTAMP, id_usuario_anula = ?, motivo_anulacion = ? WHERE id_transferencia = ?");
        try {
            p.setInt(1, idUsuario);
            p.setString(2, motivo);
            p.setInt(3, idTr);
            p.executeUpdate();
        } finally {
            p.close();
        }
        p = conn.prepareStatement(
            "UPDATE remisiones SET anulada = true, fecha_anulacion = CURRENT_TIMESTAMP, id_usuario_anula = ?, motivo_anulacion = ? " +
            "WHERE id_transferencia = ? AND anulada = false");
        try {
            p.setInt(1, idUsuario);
            p.setString(2, motivo);
            p.setInt(3, idTr);
            p.executeUpdate();
        } finally {
            p.close();
        }
    }

    private static BigDecimal precioInsumo(Connection conn, int idInsumo) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT COALESCE(precio_compra, 0) FROM insumos WHERE id_insumo = ? AND activo = true");
        try {
            p.setInt(1, idInsumo);
            ResultSet r = p.executeQuery();
            if (!r.next()) {
                throw new IllegalArgumentException("Uno de los insumos no existe o está inactivo");
            }
            BigDecimal v = r.getBigDecimal(1);
            r.close();
            return v;
        } finally {
            p.close();
        }
    }

    private static void eventoOT(Connection conn, int idOT, String tipo, int idUsuario, String detalle) throws SQLException {
        PreparedStatement p = conn.prepareStatement("INSERT INTO ot_eventos (id_ot, tipo, id_usuario, detalle) VALUES (?, ?, ?, ?)");
        try {
            p.setInt(1, idOT);
            p.setString(2, tipo);
            p.setInt(3, idUsuario);
            p.setString(4, detalle);
            p.executeUpdate();
        } finally {
            p.close();
        }
    }

    /** Ejecuta un SELECT que devuelve el siguiente número de documento. */
    private static String siguiente(Connection conn, String sql) throws SQLException {
        PreparedStatement p = conn.prepareStatement(sql);
        try {
            ResultSet r = p.executeQuery();
            r.next();
            String v = r.getString(1);
            r.close();
            return v;
        } finally {
            p.close();
        }
    }

    private static boolean existe(Connection conn, String sql, int p1) throws SQLException {
        return existe(conn, sql, p1, 0);
    }

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

    private static void responderJson(HttpServletResponse response, PrintWriter out, Object json) throws java.io.IOException {
        response.setContentType("application/json; charset=UTF-8");
        out.print(json.toString());
    }

    /** Fecha del filtro: solo 'yyyy-MM-dd' válido, si no ''. */
    private static String fechaParam(String v) {
        return (v != null && v.trim().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) ? v.trim() : "";
    }

    private static String fechaHora(Timestamp t) {
        return t == null ? "" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(t);
    }

    private static String nombreCC(String codigo, String descripcion) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return "";
        }
        return (descripcion == null || descripcion.trim().isEmpty()) ? codigo : codigo + " - " + descripcion;
    }

    private static int numero(String v) {
        return (v != null && v.trim().matches("-?[0-9]{1,9}")) ? Integer.parseInt(v.trim()) : 0;
    }

    private static String limpio(String v) {
        return v == null ? "" : v.trim();
    }

    private static String txt(String s) {
        return s == null ? "" : s;
    }

}
