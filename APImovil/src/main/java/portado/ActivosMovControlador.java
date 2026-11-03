package portado;

import inventario.Movimiento;
import inventario.Remisiones;
import java.io.PrintWriter;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
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
 * Copia de la web: Controlador/activosMovControlador.jsp
 * La lógica (validaciones, transacciones, stock y Kardex) es la misma que usa el sistema web.
 * Generado por portar.py; solo se quitaron las operaciones de impresión en PDF.
 */
@SuppressWarnings({"unused", "rawtypes"})
public final class ActivosMovControlador {

    private ActivosMovControlador() {
    }

    public static void atender(HttpServletRequest request, HttpServletResponse response, PrintWriter out, Connection conn,
                               Permisos permisos, SesionDatos session, ServletContext application) throws Exception {

    String tipo = request.getParameter("tipo");
    PreparedStatement ps = null;
    ResultSet rs = null;
    int idUsuario = ((Integer) session.getAttribute("id_usuario")).intValue();

    try {
        // ================= COMBOS =================
        if ("listarProveedores".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_proveedor, razon_social FROM proveedores WHERE activo = true ORDER BY razon_social");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar proveedor --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt(1) + "'>" + Html.esc(rs.getString(2)) + "</option>");
            }
        }
        else if ("listarCentros".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_centro, nombre FROM centros WHERE activo = true ORDER BY nombre");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar centro --</option>");
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
        else if ("listarOTAbiertas".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT o.id_ot, o.numero_ot, o.titulo, c.nombre, cc.codigo FROM ot o LEFT JOIN centros c ON c.id_centro = o.id_centro " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = o.id_centro_costo WHERE o.estado IN ('PENDIENTE', 'EN_PROCESO') ORDER BY o.fecha_solicitud, o.id_ot");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar orden de trabajo --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt(1) + "'>" + Html.esc(rs.getString(2)) + " - " + Html.esc(rs.getString(3))
                        + " (" + Html.esc(rs.getString(4)) + " / " + Html.esc(rs.getString(5)) + ")</option>");
            }
        }

        // ================= ACTIVOS PARA SELECCIONAR =================
        // Todos los activos del inventario; "seleccionable" indica si pueden entrar en una solicitud nueva.
        else if ("activos".equals(tipo)) {
            boolean baja = "true".equals(request.getParameter("baja"));
            ps = conn.prepareStatement(
                "SELECT e.id_equipo, e.serial_activo, e.numero_serie, e.estado, i.codigo, i.descripcion, c.nombre AS centro, " +
                "       e.id_centro_actual, cc.codigo AS cc_codigo, cc.descripcion AS cc_desc, " +
                "       (SELECT s.numero FROM solicitudes_activos_detalle d JOIN solicitudes_activos s ON s.id_solicitud = d.id_solicitud " +
                "         WHERE d.id_equipo = e.id_equipo AND d.estado_linea IN ('PENDIENTE', 'EN_CURSO') LIMIT 1) AS solicitud_abierta " +
                "FROM equipos e LEFT JOIN insumos i ON i.id_insumo = e.id_insumo_base " +
                "LEFT JOIN centros c ON c.id_centro = e.id_centro_actual " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = e.id_centro_costo_actual " +
                "WHERE e.estado NOT IN ('SOLICITADO', 'ANULADO', 'BAJA') AND " + permisos.sqlCentros("e.id_centro_actual") + " ORDER BY c.nombre, cc.codigo, e.serial_activo");
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject a = new JSONObject();
                String estado = rs.getString("estado");
                String abierta = rs.getString("solicitud_abierta");
                boolean libre = abierta == null && ("DISPONIBLE".equals(estado) || "ASIGNADO".equals(estado));
                a.put("id", rs.getInt("id_equipo"));
                a.put("numero_activo", txt(rs.getString("serial_activo")));
                a.put("numero_serie", txt(rs.getString("numero_serie")));
                a.put("estado", txt(estado));
                a.put("codigo", txt(rs.getString("codigo")));
                a.put("descripcion", txt(rs.getString("descripcion")));
                a.put("centro", txt(rs.getString("centro")));
                a.put("id_centro", rs.getInt("id_centro_actual"));
                a.put("centro_costo", nombreCC(rs.getString("cc_codigo"), rs.getString("cc_desc")));
                a.put("seleccionable", libre);
                a.put("motivo", abierta != null ? "En la solicitud " + abierta
                        : ("EN_REPARACION".equals(estado) ? "En reparación" : ("EN_TRANSITO".equals(estado) ? "En tránsito" : "")));
                lista.put(a);
            }
            responderJson(response, out, lista);
        }

        // ================= CREAR SOLICITUD (salida o baja) =================
        else if ("crearSolicitud".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                String tipoSol = request.getParameter("tipo_solicitud");
                if (!"REPARACION".equals(tipoSol) && !"TRANSFERENCIA".equals(tipoSol) && !"ENTREGA_OT".equals(tipoSol) && !"BAJA".equals(tipoSol)) {
                    throw new IllegalArgumentException("Seleccione el tipo de salida");
                }
                JSONArray ids = new JSONArray(request.getParameter("activos"));
                if (ids.length() == 0) {
                    throw new IllegalArgumentException("Seleccione al menos un activo");
                }
                String observacion = limpio(request.getParameter("observacion"));
                int idProveedor = 0, idCentroDest = 0, idCCDest = 0, idOT = 0;
                String motivoBaja = null;
                String[] tr = {limpio(request.getParameter("transportista_nombre")), limpio(request.getParameter("transportista_apellido")),
                               limpio(request.getParameter("transportista_ci")), limpio(request.getParameter("vehiculo_marca")),
                               limpio(request.getParameter("vehiculo_modelo")), limpio(request.getParameter("vehiculo_chapa"))};

                if ("BAJA".equals(tipoSol)) {
                    motivoBaja = request.getParameter("motivo_baja");
                    String[] validos = {"OBSOLESCENCIA", "ROTURA_IRREPARABLE", "ROBO_PERDIDA", "VENTA", "DONACION", "OTRO"};
                    boolean ok = false;
                    for (int i = 0; i < validos.length; i++) { if (validos[i].equals(motivoBaja)) ok = true; }
                    if (!ok) {
                        throw new IllegalArgumentException("Seleccione el motivo de la baja");
                    }
                    if (observacion.isEmpty()) {
                        throw new IllegalArgumentException("Describa el motivo de la baja en la observación");
                    }
                    for (int i = 0; i < tr.length; i++) { tr[i] = ""; }
                } else {
                    if (tr[0].isEmpty() || tr[1].isEmpty() || tr[2].isEmpty()) {
                        throw new IllegalArgumentException("Complete nombre, apellido y cédula de identidad del transportista");
                    }
                    if (tr[0].length() > 100 || tr[1].length() > 100 || tr[2].length() > 20 || tr[3].length() > 50 || tr[4].length() > 50 || tr[5].length() > 20) {
                        throw new IllegalArgumentException("Algún dato del transportista o del vehículo es demasiado largo");
                    }
                    if ("REPARACION".equals(tipoSol)) {
                        idProveedor = numero(request.getParameter("id_proveedor"));
                        if (idProveedor == 0 || !existe(conn, "SELECT 1 FROM proveedores WHERE id_proveedor = ? AND activo = true", idProveedor, 0)) {
                            throw new IllegalArgumentException("Seleccione el proveedor que realizará la reparación");
                        }
                    } else if ("TRANSFERENCIA".equals(tipoSol)) {
                        idCentroDest = numero(request.getParameter("id_centro_destino"));
                        idCCDest = numero(request.getParameter("id_centro_costo_destino"));
                        if (idCentroDest == 0 || idCCDest == 0) {
                            throw new IllegalArgumentException("Seleccione el centro y el centro de costo de destino");
                        }
                        if (!existe(conn, "SELECT 1 FROM centros_costo WHERE id_centro_costo = ? AND id_centro = ? AND activo = true", idCCDest, idCentroDest)) {
                            throw new IllegalArgumentException("El centro de costo no pertenece al centro de destino");
                        }
                    } else {
                        idOT = numero(request.getParameter("id_ot"));
                        ps = conn.prepareStatement("SELECT id_centro, id_centro_costo FROM ot WHERE id_ot = ? AND estado IN ('PENDIENTE', 'EN_PROCESO')");
                        ps.setInt(1, idOT);
                        rs = ps.executeQuery();
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Seleccione una orden de trabajo pendiente o en proceso");
                        }
                        idCentroDest = rs.getInt(1);
                        idCCDest = rs.getInt(2);
                        rs.close(); ps.close(); rs = null; ps = null;
                    }
                }

                String numeroSol = siguiente(conn, "BAJA".equals(tipoSol)
                        ? "SELECT 'BA-' || lpad(nextval('public.seq_baja_activo')::text, 6, '0')"
                        : "SELECT 'SA-' || lpad(nextval('public.seq_solicitud_activo')::text, 6, '0')");
                ps = conn.prepareStatement(
                    "INSERT INTO solicitudes_activos (numero, tipo, id_usuario_solicita, id_proveedor, id_centro_destino, id_centro_costo_destino, id_ot, " +
                    "motivo_baja, observacion, transportista_nombre, transportista_apellido, transportista_ci, vehiculo_marca, vehiculo_modelo, vehiculo_chapa) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_solicitud");
                ps.setString(1, numeroSol);
                ps.setString(2, tipoSol);
                ps.setInt(3, idUsuario);
                entero(ps, 4, idProveedor);
                entero(ps, 5, idCentroDest);
                entero(ps, 6, idCCDest);
                entero(ps, 7, idOT);
                texto(ps, 8, motivoBaja);
                texto(ps, 9, observacion);
                for (int i = 0; i < tr.length; i++) { texto(ps, 10 + i, tr[i]); }
                rs = ps.executeQuery();
                rs.next();
                int idSol = rs.getInt(1);
                rs.close(); ps.close(); rs = null; ps = null;

                for (int i = 0; i < ids.length(); i++) {
                    int idEquipo = ids.getInt(i);
                    ps = conn.prepareStatement("SELECT estado, id_centro_actual, id_centro_costo_actual, serial_activo FROM equipos WHERE id_equipo = ? FOR UPDATE");
                    ps.setInt(1, idEquipo);
                    rs = ps.executeQuery();
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Uno de los activos no existe");
                    }
                    String estado = rs.getString(1);
                    int centro = rs.getInt(2);
                    permisos.exigirCentro(centro);
                    int cc = rs.getInt(3);
                    boolean sinCC = rs.wasNull();
                    String numAct = rs.getString(4);
                    rs.close(); ps.close(); rs = null; ps = null;
                    if (!"DISPONIBLE".equals(estado) && !"ASIGNADO".equals(estado)) {
                        throw new IllegalArgumentException("El activo " + numAct + " no está disponible (estado: " + estado + ")");
                    }
                    if (existe(conn, "SELECT 1 FROM solicitudes_activos_detalle WHERE id_equipo = ? AND estado_linea IN ('PENDIENTE', 'EN_CURSO')", idEquipo, 0)) {
                        throw new IllegalArgumentException("El activo " + numAct + " ya está en otra solicitud abierta");
                    }
                    if ("TRANSFERENCIA".equals(tipoSol) && centro == idCentroDest) {
                        throw new IllegalArgumentException("El activo " + numAct + " ya está en el centro de destino; use \"Cambiar centro de costo\" en Listar Activos Fijos");
                    }
                    ps = conn.prepareStatement(
                        "INSERT INTO solicitudes_activos_detalle (id_solicitud, id_equipo, id_centro_origen, id_centro_costo_origen, estado_origen) VALUES (?, ?, ?, ?, ?)");
                    ps.setInt(1, idSol);
                    ps.setInt(2, idEquipo);
                    entero(ps, 3, centro);
                    entero(ps, 4, sinCC ? 0 : cc);
                    ps.setString(5, estado);
                    ps.executeUpdate();
                    ps.close(); ps = null;
                }
                conn.commit();
                resp.put("ok", true);
                resp.put("numero", numeroSol);
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

        // ================= LISTAR SOLICITUDES =================
        // estado: PENDIENTE (aprobaciones) o '' (historial)
        else if ("listarSolicitudes".equals(tipo)) {
            String estado = "PENDIENTE".equals(request.getParameter("estado")) ? "PENDIENTE" : "";
            ps = conn.prepareStatement(
                "SELECT s.id_solicitud, s.numero, s.tipo, s.estado, s.fecha, s.observacion, s.motivo_baja, s.motivo_rechazo, s.motivo_anulacion, " +
                "       s.fecha_resolucion, s.fecha_anulacion, s.id_remision, r.numero_remision, " +
                "       us.nombre || ' ' || us.apellido AS solicitante, ur.nombre || ' ' || ur.apellido AS resolvio, " +
                "       p.razon_social AS proveedor, cd.nombre AS centro_destino, ccd.codigo AS cc_destino, o.numero_ot, " +
                "       TRIM(COALESCE(s.transportista_nombre, '') || ' ' || COALESCE(s.transportista_apellido, '')) AS transportista, " +
                "       (SELECT COUNT(*) FROM solicitudes_activos_detalle d WHERE d.id_solicitud = s.id_solicitud) AS activos, " +
                "       (SELECT COUNT(*) FROM solicitudes_activos_detalle d WHERE d.id_solicitud = s.id_solicitud AND d.estado_linea IN ('RECIBIDO', 'RECHAZADO_DESTINO')) AS recibidos " +
                "FROM solicitudes_activos s LEFT JOIN remisiones r ON r.id_remision = s.id_remision " +
                "LEFT JOIN usuarios us ON us.id_usuario = s.id_usuario_solicita LEFT JOIN usuarios ur ON ur.id_usuario = s.id_usuario_resuelve " +
                "LEFT JOIN proveedores p ON p.id_proveedor = s.id_proveedor LEFT JOIN centros cd ON cd.id_centro = s.id_centro_destino " +
                "LEFT JOIN centros_costo ccd ON ccd.id_centro_costo = s.id_centro_costo_destino LEFT JOIN ot o ON o.id_ot = s.id_ot " +
                "WHERE (? = '' OR s.estado = ?) AND " + condSolicitud(permisos) + " ORDER BY s.fecha DESC, s.id_solicitud DESC");
            ps.setString(1, estado);
            ps.setString(2, estado);
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject s = new JSONObject();
                String t = rs.getString("tipo");
                s.put("id", rs.getInt("id_solicitud"));
                s.put("numero", txt(rs.getString("numero")));
                s.put("tipo", txt(t));
                s.put("estado", txt(rs.getString("estado")));
                s.put("fecha", fechaHora(rs.getTimestamp("fecha")));
                s.put("observacion", txt(rs.getString("observacion")));
                s.put("motivo_baja", txt(rs.getString("motivo_baja")));
                s.put("motivo_rechazo", txt(rs.getString("motivo_rechazo")));
                s.put("motivo_anulacion", txt(rs.getString("motivo_anulacion")));
                s.put("fecha_resolucion", fechaHora(rs.getTimestamp("fecha_resolucion")));
                s.put("fecha_anulacion", fechaHora(rs.getTimestamp("fecha_anulacion")));
                s.put("id_remision", rs.getInt("id_remision"));
                s.put("remision", txt(rs.getString("numero_remision")));
                s.put("solicitante", txt(rs.getString("solicitante")));
                s.put("resolvio", txt(rs.getString("resolvio")));
                s.put("destino", "REPARACION".equals(t) ? "Proveedor: " + txt(rs.getString("proveedor"))
                        : ("BAJA".equals(t) ? "Baja" : txt(rs.getString("centro_destino")) + " / " + txt(rs.getString("cc_destino"))
                           + (rs.getString("numero_ot") == null ? "" : " (" + rs.getString("numero_ot") + ")")));
                s.put("transportista", txt(rs.getString("transportista")));
                s.put("activos", rs.getInt("activos"));
                s.put("recibidos", rs.getInt("recibidos"));
                lista.put(s);
            }
            responderJson(response, out, lista);
        }
        else if ("detalleSolicitud".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT e.serial_activo, e.numero_serie, i.codigo, i.descripcion, co.nombre AS centro_origen, cco.codigo AS cc_origen, d.estado_origen, d.estado_linea, " +
                "       d.fecha_recepcion, d.observacion_recepcion " +
                "FROM solicitudes_activos_detalle d JOIN equipos e ON e.id_equipo = d.id_equipo LEFT JOIN insumos i ON i.id_insumo = e.id_insumo_base " +
                "LEFT JOIN centros co ON co.id_centro = d.id_centro_origen LEFT JOIN centros_costo cco ON cco.id_centro_costo = d.id_centro_costo_origen " +
                "WHERE d.id_solicitud = ? AND EXISTS (SELECT 1 FROM solicitudes_activos s WHERE s.id_solicitud = d.id_solicitud AND " + condSolicitud(permisos) + ") ORDER BY d.id_detalle");
            ps.setInt(1, numero(request.getParameter("id")));
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject a = new JSONObject();
                a.put("numero_activo", txt(rs.getString(1)));
                a.put("numero_serie", txt(rs.getString(2)));
                a.put("codigo", txt(rs.getString(3)));
                a.put("descripcion", txt(rs.getString(4)));
                a.put("origen", txt(rs.getString(5)) + (rs.getString(6) == null ? "" : " / " + rs.getString(6)));
                a.put("estado_origen", txt(rs.getString(7)));
                a.put("estado_linea", txt(rs.getString(8)));
                a.put("fecha_recepcion", fechaHora(rs.getTimestamp(9)));
                a.put("observacion_recepcion", txt(rs.getString(10)));
                lista.put(a);
            }
            responderJson(response, out, lista);
        }

        // ================= APROBAR / RECHAZAR =================
        else if ("aprobar".equals(tipo) || "rechazar".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idSol = numero(request.getParameter("id"));
                exigirOrigen(conn, permisos, idSol);
                String[] s = bloquearSolicitud(conn, idSol);
                if (!"PENDIENTE".equals(s[1])) {
                    throw new IllegalArgumentException("La solicitud ya fue resuelta (estado: " + s[1] + ")");
                }
                if ("rechazar".equals(tipo)) {
                    String motivo = limpio(request.getParameter("motivo"));
                    if (motivo.isEmpty()) {
                        throw new IllegalArgumentException("Indique el motivo del rechazo");
                    }
                    ejecutar(conn, "UPDATE solicitudes_activos SET estado = 'RECHAZADA', fecha_resolucion = CURRENT_TIMESTAMP, id_usuario_resuelve = ?, motivo_rechazo = ? WHERE id_solicitud = ?",
                             Integer.valueOf(idUsuario), motivo, Integer.valueOf(idSol));
                    ejecutar(conn, "UPDATE solicitudes_activos_detalle SET estado_linea = 'ANULADO' WHERE id_solicitud = ?", Integer.valueOf(idSol));
                } else {
                    resp.put("remision", aprobar(conn, idSol, idUsuario));
                }
                conn.commit();
                resp.put("ok", true);
                resp.put("fecha", fechaActual(conn));
                resp.put("id_remision", remisionDe(conn, idSol));
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } finally {
                conn.setAutoCommit(true);
            }
            responderJson(response, out, resp);
        }

        // ================= RECEPCIÓN =================
        // Activos en tránsito (transferencia / entrega por OT) y en reparación
        else if ("enRecepcion".equals(tipo)) {
            ps = conn.prepareStatement(
                "SELECT d.id_detalle, s.numero, s.tipo, s.fecha_resolucion, r.numero_remision, e.serial_activo, e.numero_serie, i.descripcion, " +
                "       co.nombre AS centro_origen, cco.codigo AS cc_origen, cd.nombre AS centro_destino, ccd.codigo AS cc_destino, p.razon_social AS proveedor, o.numero_ot " +
                "FROM solicitudes_activos_detalle d JOIN solicitudes_activos s ON s.id_solicitud = d.id_solicitud JOIN equipos e ON e.id_equipo = d.id_equipo " +
                "LEFT JOIN insumos i ON i.id_insumo = e.id_insumo_base LEFT JOIN remisiones r ON r.id_remision = s.id_remision " +
                "LEFT JOIN centros co ON co.id_centro = d.id_centro_origen LEFT JOIN centros_costo cco ON cco.id_centro_costo = d.id_centro_costo_origen " +
                "LEFT JOIN centros cd ON cd.id_centro = s.id_centro_destino LEFT JOIN centros_costo ccd ON ccd.id_centro_costo = s.id_centro_costo_destino " +
                "LEFT JOIN proveedores p ON p.id_proveedor = s.id_proveedor LEFT JOIN ot o ON o.id_ot = s.id_ot " +
                "WHERE d.estado_linea = 'EN_CURSO' AND s.tipo <> 'BAJA' AND " + condRecepcion(permisos) + " ORDER BY s.fecha_resolucion, d.id_detalle");
            rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject a = new JSONObject();
                String t = rs.getString("tipo");
                a.put("id", rs.getInt("id_detalle"));
                a.put("solicitud", txt(rs.getString("numero")));
                a.put("tipo", txt(t));
                a.put("fecha", fechaHora(rs.getTimestamp("fecha_resolucion")));
                a.put("remision", txt(rs.getString("numero_remision")));
                a.put("numero_activo", txt(rs.getString("serial_activo")));
                a.put("numero_serie", txt(rs.getString("numero_serie")));
                a.put("descripcion", txt(rs.getString("descripcion")));
                a.put("origen", txt(rs.getString("centro_origen")) + (rs.getString("cc_origen") == null ? "" : " / " + rs.getString("cc_origen")));
                a.put("destino", "REPARACION".equals(t) ? "Proveedor: " + txt(rs.getString("proveedor"))
                        : txt(rs.getString("centro_destino")) + " / " + txt(rs.getString("cc_destino")) + (rs.getString("numero_ot") == null ? "" : " (" + rs.getString("numero_ot") + ")"));
                lista.put(a);
            }
            responderJson(response, out, lista);
        }
        // recibir: ids = detalles; en tránsito -> DISPONIBLE en destino; reparación -> DISPONIBLE en su ubicación
        // rechazarRecepcion: solo tránsito, justificación obligatoria -> vuelve al origen
        else if ("recibir".equals(tipo) || "rechazarRecepcion".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                boolean rechazo = "rechazarRecepcion".equals(tipo);
                JSONArray ids = new JSONArray(request.getParameter("ids"));
                String obs = limpio(request.getParameter("observacion"));
                for (int ic = 0; ic < ids.length(); ic++) {
                    if (!existe(conn, "SELECT 1 FROM solicitudes_activos_detalle d JOIN solicitudes_activos s ON s.id_solicitud = d.id_solicitud "
                            + "WHERE d.id_detalle = ? AND " + condRecepcion(permisos), ids.getInt(ic), 0)) {
                        throw new IllegalArgumentException("Uno de los activos no corresponde a sus centros");
                    }
                }
                if (ids.length() == 0) {
                    throw new IllegalArgumentException("Seleccione al menos un activo");
                }
                if (rechazo && obs.isEmpty()) {
                    throw new IllegalArgumentException("La justificación del rechazo es obligatoria");
                }
                for (int i = 0; i < ids.length(); i++) {
                    recibirLinea(conn, ids.getInt(i), idUsuario, rechazo, obs);
                }
                conn.commit();
                resp.put("ok", true);
                resp.put("cantidad", ids.length());
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

        // ================= ANULAR UNA SALIDA APROBADA (y su remisión) =================
        else if ("anularSalida".equals(tipo)) {
            JSONObject resp = new JSONObject();
            conn.setAutoCommit(false);
            try {
                int idSol = numero(request.getParameter("id"));
                exigirOrigen(conn, permisos, idSol);
                String motivo = limpio(request.getParameter("motivo"));
                if (motivo.isEmpty()) {
                    throw new IllegalArgumentException("Indique el motivo de la anulación");
                }
                anularSalida(conn, idSol, idUsuario, motivo);
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

        // ================= ACTA DE BAJA (JRXML) =================
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


    /** Solicitudes visibles: salen de sus centros o van hacia ellos (requiere alias s). */
    private static String condSolicitud(Permisos p) {
        if (p.todosLosCentros()) {
            return "TRUE";
        }
        return "(" + p.sqlCentros("s.id_centro_destino") + " OR EXISTS (SELECT 1 FROM solicitudes_activos_detalle dx WHERE dx.id_solicitud = s.id_solicitud AND "
                + p.sqlCentros("dx.id_centro_origen") + "))";
    }

    /** Recepción: traslados hacia sus centros y retornos de reparación de sus centros (alias s y d). */
    private static String condRecepcion(Permisos p) {
        if (p.todosLosCentros()) {
            return "TRUE";
        }
        return "((s.tipo = 'REPARACION' AND " + p.sqlCentros("d.id_centro_origen") + ") OR (s.tipo <> 'REPARACION' AND " + p.sqlCentros("s.id_centro_destino") + "))";
    }

    /** Aprobar, rechazar o anular: solo quien opera el centro de origen de los activos. */
    private static void exigirOrigen(Connection conn, Permisos p, int idSol) throws SQLException {
        if (!p.todosLosCentros() && !existe(conn, "SELECT 1 FROM solicitudes_activos_detalle d WHERE d.id_solicitud = ? AND "
                + p.sqlCentros("d.id_centro_origen"), idSol, 0)) {
            throw new IllegalArgumentException("La solicitud es de un centro al que no tiene acceso");
        }
    }

    /** Bloquea la solicitud y devuelve {tipo, estado, numero, id_proveedor, id_centro_destino, id_cc_destino, id_ot}. */
    private static String[] bloquearSolicitud(Connection conn, int idSol) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
            "SELECT tipo, estado, numero, id_proveedor, id_centro_destino, id_centro_costo_destino, id_ot FROM solicitudes_activos WHERE id_solicitud = ? FOR UPDATE");
        try {
            p.setInt(1, idSol);
            ResultSet r = p.executeQuery();
            if (!r.next()) {
                throw new IllegalArgumentException("Solicitud no encontrada");
            }
            String[] v = new String[7];
            for (int i = 0; i < 7; i++) {
                v[i] = r.getString(i + 1);
            }
            r.close();
            return v;
        } finally {
            p.close();
        }
    }

    /** Aprueba: genera la remisión (salvo baja) y mueve cada activo. Devuelve el número de remisión. */
    private static String aprobar(Connection conn, int idSol, int idUsuario) throws SQLException {
        String[] s = bloquearSolicitud(conn, idSol);
        String tipoSol = s[0], numeroSol = s[2];
        Integer idProv = s[3] == null ? null : Integer.valueOf(s[3]);
        Integer centroDest = s[4] == null ? null : Integer.valueOf(s[4]);
        Integer ccDest = s[5] == null ? null : Integer.valueOf(s[5]);
        Integer idOT = s[6] == null ? null : Integer.valueOf(s[6]);
        boolean baja = "BAJA".equals(tipoSol);

        String proveedor = "";
        String[] tr = new String[6];
        PreparedStatement p = conn.prepareStatement(
            "SELECT s.transportista_nombre, s.transportista_apellido, s.transportista_ci, s.vehiculo_marca, s.vehiculo_modelo, s.vehiculo_chapa, " +
            "       p.razon_social, s.observacion FROM solicitudes_activos s LEFT JOIN proveedores p ON p.id_proveedor = s.id_proveedor WHERE s.id_solicitud = ?");
        String observacion;
        try {
            p.setInt(1, idSol);
            ResultSet r = p.executeQuery();
            r.next();
            for (int i = 0; i < 6; i++) {
                tr[i] = r.getString(i + 1);
            }
            proveedor = r.getString(7) == null ? "" : r.getString(7);
            observacion = r.getString(8);
            r.close();
        } finally {
            p.close();
        }

        Integer idRem = null;
        String numeroRem = "";
        if (!baja) {
            numeroRem = siguiente(conn, "SELECT 'REM-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad(nextval('public.seq_remision')::text, 6, '0')");
            p = conn.prepareStatement(
                "INSERT INTO remisiones (numero_remision, fecha_emision, fecha_hora, tipo_remision, id_ot, id_solicitud_activo, id_usuario_emite, " +
                "transportista_nombre, transportista_apellido, transportista_ci, vehiculo_marca, vehiculo_modelo, vehiculo_chapa, observacion, anulada) " +
                "VALUES (?, CURRENT_DATE, CURRENT_TIMESTAMP, 'SALIDA_ACTIVOS', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false) RETURNING id_remision");
            try {
                p.setString(1, numeroRem);
                if (idOT == null) { p.setNull(2, Types.INTEGER); } else { p.setInt(2, idOT.intValue()); }
                p.setInt(3, idSol);
                p.setInt(4, idUsuario);
                for (int i = 0; i < 6; i++) { texto(p, 5 + i, tr[i]); }
                texto(p, 11, observacion);
                ResultSet r = p.executeQuery();
                r.next();
                idRem = Integer.valueOf(r.getInt(1));
                r.close();
            } finally {
                p.close();
            }
        }

        // Movimiento de cada activo
        p = conn.prepareStatement(
            "SELECT d.id_detalle, d.id_equipo, d.id_centro_origen, d.id_centro_costo_origen, e.estado FROM solicitudes_activos_detalle d " +
            "JOIN equipos e ON e.id_equipo = d.id_equipo WHERE d.id_solicitud = ? AND d.estado_linea = 'PENDIENTE' FOR UPDATE OF e");
        List<int[]> lineas = new ArrayList<int[]>();
        List<String> estados = new ArrayList<String>();
        try {
            p.setInt(1, idSol);
            ResultSet r = p.executeQuery();
            while (r.next()) {
                lineas.add(new int[]{r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4)});
                estados.add(r.getString(5));
            }
            r.close();
        } finally {
            p.close();
        }
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("La solicitud no tiene activos pendientes");
        }
        for (int i = 0; i < lineas.size(); i++) {
            int[] ln = lineas.get(i);
            String estadoAct = estados.get(i);
            if (!"DISPONIBLE".equals(estadoAct) && !"ASIGNADO".equals(estadoAct)) {
                throw new IllegalArgumentException("Un activo cambió de estado (" + estadoAct + ") desde que se hizo la solicitud; rechácela y cree una nueva");
            }
            String tipoMov = baja ? Movimiento.BAJA : ("REPARACION".equals(tipoSol) ? Movimiento.REPARACION : Movimiento.ENVIO);
            String nuevoEstado = baja ? "BAJA" : ("REPARACION".equals(tipoSol) ? "EN_REPARACION" : "EN_TRANSITO");
            Movimiento m = new Movimiento(tipoMov, ln[1], idUsuario);
            m.estadoAnterior = estadoAct;
            m.estadoNuevo = nuevoEstado;
            m.centroOrigen = ln[2] == 0 ? null : Integer.valueOf(ln[2]);
            m.centroCostoOrigen = ln[3] == 0 ? null : Integer.valueOf(ln[3]);
            m.usuarioEmisor = Integer.valueOf(idUsuario);
            m.idRemision = idRem;
            if (!baja) {
                m.receptorNombre = tr[0];
                m.receptorApellido = tr[1];
                m.receptorCi = tr[2];
                m.vehiculoMarca = tr[3];
                m.vehiculoModelo = tr[4];
                m.vehiculoChapa = tr[5];
            }
            if ("REPARACION".equals(tipoSol)) {
                m.observaciones = numeroSol + " - Reparación en " + proveedor + (observacion == null ? "" : ". " + observacion);
            } else if (baja) {
                m.observaciones = numeroSol + " - Baja: " + (observacion == null ? "" : observacion);
            } else {
                m.centroDestino = centroDest;
                m.centroCostoDestino = ccDest;
                m.observaciones = numeroSol + ("ENTREGA_OT".equals(tipoSol) ? " - Entrega por OT" : " - Transferencia entre sucursales")
                        + (observacion == null ? "" : ". " + observacion);
            }
            m.registrar(conn);
            ejecutar(conn, "UPDATE equipos SET estado = ?, activo = ? WHERE id_equipo = ?", nuevoEstado, Boolean.valueOf(!baja), Integer.valueOf(ln[1]));
            ejecutar(conn, "UPDATE solicitudes_activos_detalle SET estado_linea = ? WHERE id_detalle = ?", baja ? "FINALIZADO" : "EN_CURSO", Integer.valueOf(ln[0]));
        }
        ejecutar(conn, "UPDATE solicitudes_activos SET estado = 'APROBADA', fecha_resolucion = CURRENT_TIMESTAMP, id_usuario_resuelve = ?, id_remision = ? WHERE id_solicitud = ?",
                 Integer.valueOf(idUsuario), idRem, Integer.valueOf(idSol));
        if (idRem != null) {
            Remisiones.copiarDetalle(conn, idRem.intValue()); // detalle propio de la remisión (remisiones_detalle)
        }
        if (idOT != null) {
            ejecutar(conn, "INSERT INTO ot_eventos (id_ot, tipo, id_usuario, detalle) VALUES (?, 'SALIDA', ?, ?)",
                     idOT, Integer.valueOf(idUsuario), "Salida de " + lineas.size() + " activo(s) con la remisión " + numeroRem + " (" + numeroSol + ")");
        }
        return numeroRem;
    }

    /** Recibe (o rechaza en destino) una línea en curso. */
    private static void recibirLinea(Connection conn, int idDetalle, int idUsuario, boolean rechazo, String obs) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
            "SELECT d.id_equipo, d.id_centro_origen, d.id_centro_costo_origen, d.estado_origen, d.estado_linea, s.tipo, s.numero, " +
            "       s.id_centro_destino, s.id_centro_costo_destino, s.id_remision, e.estado, e.serial_activo, e.id_centro_actual, e.id_centro_costo_actual " +
            "FROM solicitudes_activos_detalle d JOIN solicitudes_activos s ON s.id_solicitud = d.id_solicitud JOIN equipos e ON e.id_equipo = d.id_equipo " +
            "WHERE d.id_detalle = ? FOR UPDATE OF d, e");
        int idEquipo, centroOri, ccOri, centroDest, ccDest, idRem, centroAct, ccAct;
        String estadoOri, estadoLinea, tipoSol, numeroSol, estadoAct, numAct;
        try {
            p.setInt(1, idDetalle);
            ResultSet r = p.executeQuery();
            if (!r.next()) {
                throw new IllegalArgumentException("Uno de los activos ya no está pendiente de recepción");
            }
            idEquipo = r.getInt(1); centroOri = r.getInt(2); ccOri = r.getInt(3); estadoOri = r.getString(4);
            estadoLinea = r.getString(5); tipoSol = r.getString(6); numeroSol = r.getString(7);
            centroDest = r.getInt(8); ccDest = r.getInt(9); idRem = r.getInt(10);
            estadoAct = r.getString(11); numAct = r.getString(12); centroAct = r.getInt(13); ccAct = r.getInt(14);
            r.close();
        } finally {
            p.close();
        }
        if (!"EN_CURSO".equals(estadoLinea)) {
            throw new IllegalArgumentException("El activo " + numAct + " ya fue recibido o su salida fue anulada");
        }
        boolean reparacion = "REPARACION".equals(tipoSol);
        if (rechazo && reparacion) {
            throw new IllegalArgumentException("Un activo que vuelve de reparación se recibe; no se rechaza (" + numAct + ")");
        }
        Movimiento m;
        String nuevoEstado;
        Integer nuevoCentro, nuevoCC;
        if (reparacion) {
            m = new Movimiento(Movimiento.RETORNO_REPARACION, idEquipo, idUsuario);
            nuevoEstado = "DISPONIBLE";
            nuevoCentro = centroAct == 0 ? null : Integer.valueOf(centroAct);
            nuevoCC = ccAct == 0 ? null : Integer.valueOf(ccAct);
            m.centroDestino = nuevoCentro;
            m.centroCostoDestino = nuevoCC;
            m.observaciones = numeroSol + " - Retorno de reparación" + (obs.isEmpty() ? "" : ": " + obs);
        } else if (!rechazo) {
            m = new Movimiento(Movimiento.RECEPCION, idEquipo, idUsuario);
            nuevoEstado = "DISPONIBLE";
            nuevoCentro = Integer.valueOf(centroDest);
            nuevoCC = Integer.valueOf(ccDest);
            m.centroOrigen = centroOri == 0 ? null : Integer.valueOf(centroOri);
            m.centroCostoOrigen = ccOri == 0 ? null : Integer.valueOf(ccOri);
            m.centroDestino = nuevoCentro;
            m.centroCostoDestino = nuevoCC;
            m.observaciones = numeroSol + " - Recepción en destino" + (obs.isEmpty() ? "" : ": " + obs);
        } else {
            m = new Movimiento(Movimiento.RECHAZO, idEquipo, idUsuario);
            nuevoEstado = estadoOri == null ? "DISPONIBLE" : estadoOri;
            nuevoCentro = centroOri == 0 ? null : Integer.valueOf(centroOri);
            nuevoCC = ccOri == 0 ? null : Integer.valueOf(ccOri);
            m.centroOrigen = Integer.valueOf(centroDest);
            m.centroCostoOrigen = Integer.valueOf(ccDest);
            m.centroDestino = nuevoCentro;
            m.centroCostoDestino = nuevoCC;
            m.justificacionRechazo = obs;
            m.observaciones = numeroSol + " - Rechazado en destino; vuelve al origen";
        }
        m.estadoAnterior = estadoAct;
        m.estadoNuevo = nuevoEstado;
        m.usuarioReceptor = Integer.valueOf(idUsuario);
        m.idRemision = idRem == 0 ? null : Integer.valueOf(idRem);
        m.registrar(conn);
        ejecutar(conn, "UPDATE equipos SET estado = ?, id_centro_actual = ?, id_centro_costo_actual = ? WHERE id_equipo = ?",
                 nuevoEstado, nuevoCentro, nuevoCC, Integer.valueOf(idEquipo));
        ejecutar(conn, "UPDATE solicitudes_activos_detalle SET estado_linea = ?, fecha_recepcion = CURRENT_TIMESTAMP, id_usuario_recibe = ?, observacion_recepcion = ? WHERE id_detalle = ?",
                 rechazo ? "RECHAZADO_DESTINO" : "RECIBIDO", Integer.valueOf(idUsuario), obs.isEmpty() ? null : obs, Integer.valueOf(idDetalle));
    }

    /** Anula una salida aprobada sin recepciones: remisión anulada y activos a su origen. */
    private static void anularSalida(Connection conn, int idSol, int idUsuario, String motivo) throws SQLException {
        String[] s = bloquearSolicitud(conn, idSol);
        if (!"APROBADA".equals(s[1]) || "BAJA".equals(s[0])) {
            throw new IllegalArgumentException("Solo se puede anular una salida aprobada (las bajas son definitivas)");
        }
        if (existe(conn, "SELECT 1 FROM solicitudes_activos_detalle WHERE id_solicitud = ? AND estado_linea <> 'EN_CURSO'", idSol, 0)) {
            throw new IllegalArgumentException("Algún activo de esta salida ya fue recibido o rechazado en destino; no se puede anular la remisión");
        }
        PreparedStatement p = conn.prepareStatement(
            "SELECT d.id_detalle, d.id_equipo, d.id_centro_origen, d.id_centro_costo_origen, d.estado_origen, e.estado, s.id_remision " +
            "FROM solicitudes_activos_detalle d JOIN equipos e ON e.id_equipo = d.id_equipo JOIN solicitudes_activos s ON s.id_solicitud = d.id_solicitud " +
            "WHERE d.id_solicitud = ? FOR UPDATE OF e");
        List<Object[]> lineas = new ArrayList<Object[]>();
        int idRem = 0;
        try {
            p.setInt(1, idSol);
            ResultSet r = p.executeQuery();
            while (r.next()) {
                lineas.add(new Object[]{Integer.valueOf(r.getInt(1)), Integer.valueOf(r.getInt(2)), Integer.valueOf(r.getInt(3)), Integer.valueOf(r.getInt(4)), r.getString(5), r.getString(6)});
                idRem = r.getInt(7);
            }
            r.close();
        } finally {
            p.close();
        }
        for (Object[] ln : lineas) {
            int idEquipo = ((Integer) ln[1]).intValue();
            int centro = ((Integer) ln[2]).intValue();
            int cc = ((Integer) ln[3]).intValue();
            String estadoOri = ln[4] == null ? "DISPONIBLE" : (String) ln[4];
            Movimiento m = new Movimiento(Movimiento.ANULACION_SALIDA, idEquipo, idUsuario);
            m.estadoAnterior = (String) ln[5];
            m.estadoNuevo = estadoOri;
            m.centroDestino = centro == 0 ? null : Integer.valueOf(centro);
            m.centroCostoDestino = cc == 0 ? null : Integer.valueOf(cc);
            m.idRemision = idRem == 0 ? null : Integer.valueOf(idRem);
            m.observaciones = s[2] + " - Salida anulada: " + motivo;
            m.registrar(conn);
            ejecutar(conn, "UPDATE equipos SET estado = ?, id_centro_actual = ?, id_centro_costo_actual = ? WHERE id_equipo = ?",
                     estadoOri, m.centroDestino, m.centroCostoDestino, Integer.valueOf(idEquipo));
            ejecutar(conn, "UPDATE solicitudes_activos_detalle SET estado_linea = 'ANULADO' WHERE id_detalle = ?", ln[0]);
        }
        ejecutar(conn, "UPDATE solicitudes_activos SET estado = 'ANULADA', fecha_anulacion = CURRENT_TIMESTAMP, id_usuario_anula = ?, motivo_anulacion = ? WHERE id_solicitud = ?",
                 Integer.valueOf(idUsuario), motivo, Integer.valueOf(idSol));
        if (idRem != 0) {
            ejecutar(conn, "UPDATE remisiones SET anulada = true, fecha_anulacion = CURRENT_TIMESTAMP, id_usuario_anula = ?, motivo_anulacion = ? WHERE id_remision = ?",
                     Integer.valueOf(idUsuario), motivo, Integer.valueOf(idRem));
        }
        if (s[6] != null) {
            ejecutar(conn, "INSERT INTO ot_eventos (id_ot, tipo, id_usuario, detalle) VALUES (?, 'ANULACION_REMISION', ?, ?)",
                     Integer.valueOf(s[6]), Integer.valueOf(idUsuario), "Salida de activos " + s[2] + " anulada: " + motivo);
        }
    }

    private static int remisionDe(Connection conn, int idSol) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT COALESCE(id_remision, 0) FROM solicitudes_activos WHERE id_solicitud = ?");
        try {
            p.setInt(1, idSol);
            ResultSet r = p.executeQuery();
            int v = r.next() ? r.getInt(1) : 0;
            r.close();
            return v;
        } finally {
            p.close();
        }
    }

    /** Ejecuta un UPDATE/INSERT con parámetros String, Integer, Boolean o null. */
    private static void ejecutar(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement p = conn.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                Object v = params[i];
                if (v == null) { p.setNull(i + 1, Types.NULL); }
                else if (v instanceof Integer) { p.setInt(i + 1, ((Integer) v).intValue()); }
                else if (v instanceof Boolean) { p.setBoolean(i + 1, ((Boolean) v).booleanValue()); }
                else { p.setString(i + 1, v.toString()); }
            }
            p.executeUpdate();
        } finally {
            p.close();
        }
    }

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

    private static void entero(PreparedStatement p, int i, int v) throws SQLException {
        if (v == 0) { p.setNull(i, Types.INTEGER); } else { p.setInt(i, v); }
    }

    private static void texto(PreparedStatement p, int i, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) { p.setNull(i, Types.VARCHAR); } else { p.setString(i, v.trim()); }
    }

    private static String fechaActual(Connection conn) throws SQLException {
        return siguiente(conn, "SELECT to_char(CURRENT_TIMESTAMP, 'DD/MM/YYYY HH24:MI')");
    }

    private static void responderJson(HttpServletResponse response, PrintWriter out, Object json) throws java.io.IOException {
        response.setContentType("application/json; charset=UTF-8");
        out.print(json.toString());
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
        return (v != null && v.trim().matches("[0-9]{1,9}")) ? Integer.parseInt(v.trim()) : 0;
    }

    private static String limpio(String v) {
        return v == null ? "" : v.trim();
    }

    private static String txt(String s) {
        return s == null ? "" : s;
    }

}
