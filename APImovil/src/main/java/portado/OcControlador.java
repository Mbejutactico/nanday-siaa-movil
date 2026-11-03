package portado;

import inventario.Movimiento;
import inventario.MovimientoStock;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.*;
import seguridad.Permisos;
import seguridad.SesionDatos;
import util.Errores;
import util.Html;
import util.Seriales;

/**
 * Copia de la web: Controlador/ocControlador.jsp
 * La lógica (validaciones, transacciones, stock y Kardex) es la misma que usa el sistema web.
 * Generado por portar.py; solo se quitaron las operaciones de impresión en PDF.
 */
@SuppressWarnings({"unused", "rawtypes"})
public final class OcControlador {

    private OcControlador() {
    }

    public static void atender(HttpServletRequest request, HttpServletResponse response, PrintWriter out, Connection conn,
                               Permisos permisos, SesionDatos session, ServletContext application) throws Exception {

    String tipo = request.getParameter("tipo");
    PreparedStatement ps = null;
    ResultSet rs = null;

    try {
        // ---------- LISTAR OC ----------
        if ("listar".equals(tipo)) {
            String sql = "SELECT c.id_compra, c.numero_oc, c.fecha_compra, p.razon_social AS proveedor, " +
                         "cen.nombre AS centro, u.nombre || ' ' || u.apellido AS solicitante, c.estado, " +
                         "COALESCE(SUM(d.precio_unitario * d.cantidad), 0) AS total " +
                         "FROM compras c " +
                         "LEFT JOIN proveedores p ON c.id_proveedor = p.id_proveedor " +
                         "LEFT JOIN centros cen ON c.id_centro_destino = cen.id_centro " +
                         "LEFT JOIN usuarios u ON c.id_usuario_solicitante = u.id_usuario " +
                         "LEFT JOIN detalle_compra d ON c.id_compra = d.id_compra " +
                         "WHERE c.numero_oc IS NOT NULL AND c.numero_oc != '' AND " + permisos.sqlCentros("c.id_centro_destino") + " " +
                         "GROUP BY c.id_compra, c.numero_oc, c.fecha_compra, p.razon_social, cen.nombre, u.nombre, u.apellido, c.estado " +
                         "ORDER BY c.fecha_compra DESC, c.numero_oc DESC";
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                String estado = rs.getString("estado");
                out.print("<tr data-id='" + rs.getInt("id_compra") + "' style='cursor:pointer;'>");
                // El span lleva el id a la fila de DataTables (clic en la fila = ver ficha)
                out.print("<td><span class='fila-oc' data-id='" + rs.getInt("id_compra") + "'></span><strong>" + Html.esc(rs.getString("numero_oc")) + "</strong></td>");
                out.print("<td>" + Html.esc(rs.getDate("fecha_compra")) + "</td>");
                out.print("<td>" + Html.esc(rs.getString("proveedor")) + "</td>");
                out.print("<td>" + Html.esc(rs.getString("centro")) + "</td>");
                out.print("<td>" + Html.esc(rs.getString("solicitante")) + "</td>");
                out.print("<td><span class='badge " + getBadgeColor(estado) + "'>" + Html.esc(estado) + "</span></td>");
                out.print("<td>" + rs.getBigDecimal("total").toPlainString() + "</td>");

                // Celda de acciones
                out.print("<td class='text-nowrap'>");
                out.print("<button type='button' class='btn btn-secondary btn-sm' onclick='verFichaOC(" + rs.getInt("id_compra") + ")' title='Ver detalle'><i class='fa fa-eye'></i></button> ");
                out.print("<button type='button' class='btn btn-info btn-sm' onclick='imprimirOC(" + rs.getInt("id_compra") + ")' title='Imprimir OC'><i class='fa fa-print'></i></button> ");
                if (!"ANULADO".equals(estado) && permisos.tiene(Permisos.OC_MODIFICAR)) {
                    out.print("<button type='button' class='btn btn-warning btn-sm' onclick='editarOC(" + rs.getInt("id_compra") + ")'><i class='fa fa-edit'></i></button> ");
                } else {
                    out.print("<span class='text-muted'><i class='fa fa-edit'></i></span> ");
                }
                if (!"ANULADO".equals(estado) && !"RECIBIDO".equals(estado) && permisos.tiene(Permisos.OC_MODIFICAR)) {
                    out.print("<button type='button' class='btn btn-danger btn-sm' onclick='anularOC(" + rs.getInt("id_compra") + ")'><i class='fa fa-ban'></i></button>");
                } else {
                    out.print("<span class='text-muted'><i class='fa fa-ban'></i></span>");
                }
                out.print("</td>");
                out.print("</tr>");
            }
        }

        // ---------- CARGAR COMBOS PARA EDICIÓN ----------
        else if ("cargarCombos".equals(tipo)) {
            JSONObject combos = new JSONObject();
            StringBuilder htmlProv = new StringBuilder("<option value=''>-- Seleccionar --</option>");
            ps = conn.prepareStatement("SELECT id_proveedor, razon_social FROM proveedores WHERE activo=true ORDER BY razon_social");
            rs = ps.executeQuery();
            while (rs.next()) {
                htmlProv.append("<option value='").append(rs.getInt("id_proveedor")).append("'>").append(Html.esc(rs.getString("razon_social"))).append("</option>");
            }
            rs.close(); ps.close(); rs = null; ps = null;

            StringBuilder htmlCent = new StringBuilder("<option value=''>-- Seleccionar --</option>");
            ps = conn.prepareStatement("SELECT id_centro, nombre FROM centros WHERE activo=true ORDER BY nombre");
            rs = ps.executeQuery();
            while (rs.next()) {
                htmlCent.append("<option value='").append(rs.getInt("id_centro")).append("'>").append(Html.esc(rs.getString("nombre"))).append("</option>");
            }

            combos.put("proveedores", htmlProv.toString());
            combos.put("centros", htmlCent.toString());
            out.print(combos.toString());
        }

        // ---------- CARGAR INSUMOS PARA COMBO DE AGREGAR ----------
        else if ("cargarInsumos".equals(tipo)) {
            StringBuilder html = new StringBuilder("<option value=''>-- Seleccionar --</option>");
            ps = conn.prepareStatement("SELECT id_insumo, descripcion, precio_compra FROM insumos WHERE activo=true ORDER BY descripcion");
            rs = ps.executeQuery();
            while (rs.next()) {
                BigDecimal precio = rs.getBigDecimal("precio_compra");
                html.append("<option value='").append(rs.getInt("id_insumo")).append("' data-precio='")
                    .append(precio == null ? "0" : precio.toPlainString()).append("'>")
                    .append(Html.esc(rs.getString("descripcion"))).append("</option>");
            }
            out.print(html.toString());
        }

        // ---------- OBTENER UNA OC (cabecera + detalle con insumos y equipos) ----------
        else if ("obtenerOC".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id"));
            JSONObject data = new JSONObject();

            ps = conn.prepareStatement("SELECT id_proveedor, id_centro_destino, observaciones, estado FROM compras WHERE id_compra = ?");
            ps.setInt(1, idCompra);
            rs = ps.executeQuery();
            if (rs.next()) {
                data.put("id_compra", idCompra);
                data.put("id_proveedor", rs.getInt("id_proveedor"));
                data.put("id_centro_destino", rs.getInt("id_centro_destino"));
                data.put("observaciones", rs.getString("observaciones") == null ? "" : rs.getString("observaciones"));
                data.put("estado", rs.getString("estado"));
            } else {
                data.put("error", "OC no encontrada");
                out.print(data.toString());
                return;
            }
            rs.close(); ps.close(); rs = null; ps = null;

            data.put("detalle", detalleOC(conn, idCompra));
            out.print(data.toString());
        }

        // ---------- FICHA DE LA OC (modal de detalle, como la ficha de OT) ----------
        else if ("fichaOC".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id").trim());
            JSONObject data = new JSONObject();
            ps = conn.prepareStatement(
                "SELECT c.numero_oc, c.fecha_compra, c.estado, c.observaciones, c.id_centro_destino, " +
                "       p.razon_social, p.ruc, p.telefono, p.email AS prov_email, p.direccion, " +
                "       ce.nombre AS centro, u.nombre || ' ' || u.apellido AS solicitante, " +
                "       uc.nombre || ' ' || uc.apellido AS comprador " +
                "FROM compras c LEFT JOIN proveedores p ON p.id_proveedor = c.id_proveedor " +
                "LEFT JOIN centros ce ON ce.id_centro = c.id_centro_destino " +
                "LEFT JOIN usuarios u ON u.id_usuario = c.id_usuario_solicitante " +
                "LEFT JOIN usuarios uc ON uc.id_usuario = c.id_comprador " +
                "WHERE c.id_compra = ?");
            ps.setInt(1, idCompra);
            rs = ps.executeQuery();
            if (!rs.next()) {
                data.put("error", "OC no encontrada"); out.print(data.toString()); return;
            }
            if (!permisos.veCentro(rs.getInt("id_centro_destino"))) {
                data.put("error", "Sin acceso a este centro"); out.print(data.toString()); return;
            }
            int idCentroFicha = rs.getInt("id_centro_destino");
            data.put("numero_oc", rs.getString("numero_oc") == null ? "" : rs.getString("numero_oc"));
            data.put("fecha", rs.getDate("fecha_compra") == null ? "" : new java.text.SimpleDateFormat("dd/MM/yyyy").format(rs.getDate("fecha_compra")));
            data.put("estado", rs.getString("estado") == null ? "" : rs.getString("estado"));
            data.put("observaciones", rs.getString("observaciones") == null ? "" : rs.getString("observaciones"));
            data.put("centro", rs.getString("centro") == null ? "" : rs.getString("centro"));
            data.put("solicitante", rs.getString("solicitante") == null ? "" : rs.getString("solicitante"));
            data.put("comprador", rs.getString("comprador") == null ? "" : rs.getString("comprador"));
            data.put("proveedor", rs.getString("razon_social") == null ? "" : rs.getString("razon_social"));
            data.put("ruc", rs.getString("ruc") == null ? "" : rs.getString("ruc"));
            data.put("telefono", rs.getString("telefono") == null ? "" : rs.getString("telefono"));
            data.put("prov_email", rs.getString("prov_email") == null ? "" : rs.getString("prov_email"));
            data.put("direccion", rs.getString("direccion") == null ? "" : rs.getString("direccion"));
            rs.close(); ps.close(); rs = null; ps = null;
            // Detalle de líneas con estado de recepción
            ps = conn.prepareStatement(
                "SELECT d.tipo_item, COALESCE(i.codigo, ib.codigo) AS codigo, " +
                "       COALESCE(i.descripcion, ib.descripcion) AS descripcion, " +
                "       d.cantidad, d.cantidad_recibida, d.precio_unitario, " +
                "       (d.precio_unitario * d.cantidad) AS subtotal, e.serial_activo " +
                "FROM detalle_compra d LEFT JOIN insumos i ON i.id_insumo = d.id_insumo " +
                "LEFT JOIN equipos e ON e.id_equipo = d.id_equipo " +
                "LEFT JOIN insumos ib ON ib.id_insumo = e.id_insumo_base " +
                "WHERE d.id_compra = ? ORDER BY d.id_detalle_compra");
            ps.setInt(1, idCompra);
            rs = ps.executeQuery();
            JSONArray lineas = new JSONArray();
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            while (rs.next()) {
                JSONObject ln = new JSONObject();
                ln.put("tipo", rs.getString("tipo_item") == null ? "" : rs.getString("tipo_item"));
                ln.put("codigo", rs.getString("codigo") == null ? "" : rs.getString("codigo"));
                ln.put("descripcion", rs.getString("descripcion") == null ? "" : rs.getString("descripcion"));
                ln.put("cantidad", rs.getInt("cantidad"));
                ln.put("recibido", rs.getInt("cantidad_recibida"));
                ln.put("precio", rs.getBigDecimal("precio_unitario") == null ? "0" : rs.getBigDecimal("precio_unitario").toPlainString());
                ln.put("subtotal", rs.getBigDecimal("subtotal") == null ? "0" : rs.getBigDecimal("subtotal").toPlainString());
                String serial2 = rs.getString("serial_activo");
                ln.put("serial", serial2 == null || serial2.startsWith("PEND-") ? "" : serial2);
                if (rs.getBigDecimal("subtotal") != null) total = total.add(rs.getBigDecimal("subtotal"));
                lineas.put(ln);
            }
            data.put("lineas", lineas);
            data.put("total", total.toPlainString());
            rs.close(); ps.close(); rs = null; ps = null;

            // Recepciones (vigentes y anuladas) con lo que ingresó en cada una
            String estadoOC = data.getString("estado");
            boolean puedeAnular = permisos.tiene(Permisos.OC_ANULAR_RECEPCION)
                    && ("PARCIAL".equals(estadoOC) || "RECIBIDO".equals(estadoOC));
            boolean faltaAlmacen = false;
            JSONArray recepciones = new JSONArray();
            ps = conn.prepareStatement(
                "SELECT r.id_recepcion, r.numero, r.fecha, r.historica, r.anulada, r.fecha_anulacion, r.motivo_anulacion, " +
                "       u.nombre || ' ' || u.apellido AS usuario, ua.nombre || ' ' || ua.apellido AS anulado_por " +
                "FROM recepciones_oc r LEFT JOIN usuarios u ON u.id_usuario = r.id_usuario " +
                "LEFT JOIN usuarios ua ON ua.id_usuario = r.id_usuario_anula " +
                "WHERE r.id_compra = ? ORDER BY r.fecha, r.id_recepcion");
            ps.setInt(1, idCompra);
            rs = ps.executeQuery();
            Map<Integer, JSONObject> porId = new LinkedHashMap<Integer, JSONObject>();
            SimpleDateFormat fh = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            while (rs.next()) {
                JSONObject r = new JSONObject();
                r.put("id", rs.getInt("id_recepcion"));
                r.put("numero", rs.getString("numero"));
                r.put("historica", rs.getBoolean("historica"));
                r.put("fecha", rs.getBoolean("historica")
                        ? new SimpleDateFormat("dd/MM/yyyy").format(rs.getTimestamp("fecha"))
                        : fh.format(rs.getTimestamp("fecha")));
                r.put("usuario", rs.getString("usuario") == null ? "" : rs.getString("usuario"));
                r.put("anulada", rs.getBoolean("anulada"));
                r.put("fecha_anulacion", rs.getTimestamp("fecha_anulacion") == null ? "" : fh.format(rs.getTimestamp("fecha_anulacion")));
                r.put("anulado_por", rs.getString("anulado_por") == null ? "" : rs.getString("anulado_por"));
                r.put("motivo_anulacion", rs.getString("motivo_anulacion") == null ? "" : rs.getString("motivo_anulacion"));
                r.put("puede_anular", puedeAnular && !rs.getBoolean("anulada"));
                r.put("lineas", new JSONArray());
                porId.put(Integer.valueOf(rs.getInt("id_recepcion")), r);
                recepciones.put(r);
            }
            rs.close(); ps.close(); rs = null; ps = null;
            ps = conn.prepareStatement(
                "SELECT rd.id_recepcion, rd.id, d.tipo_item, COALESCE(i.codigo, ib.codigo) AS codigo, " +
                "       COALESCE(i.descripcion, ib.descripcion) AS descripcion, rd.cantidad, a.nombre AS almacen, " +
                "       cc.codigo AS cc_codigo, cc.descripcion AS cc_desc, rd.numero_activo " +
                "FROM recepciones_oc_detalle rd JOIN recepciones_oc r ON r.id_recepcion = rd.id_recepcion " +
                "JOIN detalle_compra d ON d.id_detalle_compra = rd.id_detalle_compra " +
                "LEFT JOIN insumos i ON i.id_insumo = d.id_insumo LEFT JOIN equipos e ON e.id_equipo = d.id_equipo " +
                "LEFT JOIN insumos ib ON ib.id_insumo = e.id_insumo_base " +
                "LEFT JOIN almacenes a ON a.id_almacen = rd.id_almacen " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = rd.id_centro_costo " +
                "WHERE r.id_compra = ? ORDER BY rd.id");
            ps.setInt(1, idCompra);
            rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject r = porId.get(Integer.valueOf(rs.getInt("id_recepcion")));
                JSONObject l = new JSONObject();
                String tipoItem = rs.getString("tipo_item");
                l.put("id", rs.getInt("id"));
                l.put("tipo", tipoItem);
                l.put("codigo", rs.getString("codigo") == null ? "" : rs.getString("codigo"));
                l.put("descripcion", rs.getString("descripcion") == null ? "" : rs.getString("descripcion"));
                l.put("cantidad", rs.getInt("cantidad"));
                l.put("almacen", rs.getString("almacen") == null ? "" : rs.getString("almacen"));
                String ccCod = rs.getString("cc_codigo");
                String ccDesc = rs.getString("cc_desc");
                l.put("centro_costo", ccCod == null ? "" : ccCod + (ccDesc == null || ccDesc.trim().isEmpty() ? "" : " - " + ccDesc));
                String act = rs.getString("numero_activo");
                l.put("numero_activo", act == null || act.startsWith("PEND-") ? "" : act);
                boolean sinAlmacen = "INSUMO".equals(tipoItem) && rs.getString("almacen") == null;
                l.put("sin_almacen", sinAlmacen);
                if (sinAlmacen && !r.getBoolean("anulada")) {
                    faltaAlmacen = true;
                }
                r.getJSONArray("lineas").put(l);
            }
            rs.close(); ps.close(); rs = null; ps = null;
            data.put("recepciones", recepciones);

            // Recepciones antiguas sin almacén registrado: al anularlas hay que indicar de qué almacén sale el stock
            if (faltaAlmacen && puedeAnular) {
                StringBuilder alm = new StringBuilder("<option value=''>-- Almacén --</option>");
                ps = conn.prepareStatement("SELECT id_almacen, nombre FROM almacenes WHERE id_centro = ? ORDER BY activo DESC, nombre");
                ps.setInt(1, idCentroFicha);
                rs = ps.executeQuery();
                while (rs.next()) {
                    alm.append("<option value='").append(rs.getInt(1)).append("'>").append(Html.esc(rs.getString(2))).append("</option>");
                }
                data.put("almacenes", alm.toString());
            }
            out.print(data.toString());
        }

        // ---------- ANULAR UNA RECEPCIÓN (con justificación) ----------
        // Revierte lo que ingresó en esa recepción: descuenta el stock (Kardex: ANULACION_RECEPCION)
        // y los activos vuelven a SOLICITADO. La OC queda PARCIAL o SOLICITADO según lo que siga recibido.
        // almacenes = {"id_linea_recepcion": id_almacen} solo para recepciones antiguas sin almacén registrado.
        else if ("anularRecepcion".equals(tipo)) {
            int idRecepcion = Integer.parseInt(request.getParameter("id_recepcion").trim());
            String motivo = request.getParameter("motivo") == null ? "" : request.getParameter("motivo").trim();
            JSONObject almacenesElegidos = new JSONObject(
                    request.getParameter("almacenes") == null || request.getParameter("almacenes").trim().isEmpty()
                    ? "{}" : request.getParameter("almacenes"));

            conn.setAutoCommit(false);
            try {
                if (motivo.length() < 10) {
                    throw new IllegalArgumentException("Escriba la justificación de la anulación (al menos 10 caracteres).");
                }
                if (motivo.length() > 1000) {
                    throw new IllegalArgumentException("La justificación puede tener hasta 1000 caracteres.");
                }
                int idUsuarioSesion = ((Integer) session.getAttribute("id_usuario")).intValue();

                // La recepción y su OC, bloqueadas mientras se anula
                ps = conn.prepareStatement(
                    "SELECT r.id_compra, r.numero, r.fecha, r.anulada, c.estado, c.numero_oc, c.id_centro_destino " +
                    "FROM recepciones_oc r JOIN compras c ON c.id_compra = r.id_compra " +
                    "WHERE r.id_recepcion = ? FOR UPDATE OF r, c");
                ps.setInt(1, idRecepcion);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("La recepción no existe.");
                }
                int idCompra = rs.getInt("id_compra");
                String numeroRecepcion = rs.getString("numero");
                Timestamp fechaRecepcion = rs.getTimestamp("fecha");
                boolean yaAnulada = rs.getBoolean("anulada");
                String estadoOC = rs.getString("estado");
                String numeroOC = rs.getString("numero_oc");
                int idCentroDestino = rs.getInt("id_centro_destino");
                rs.close(); ps.close(); rs = null; ps = null;

                permisos.exigirCentro(idCentroDestino);
                if (yaAnulada) {
                    throw new IllegalArgumentException("La recepción " + numeroRecepcion + " ya está anulada.");
                }
                if (!"PARCIAL".equals(estadoOC) && !"RECIBIDO".equals(estadoOC)) {
                    throw new IllegalArgumentException("Solo se pueden anular recepciones de una OC en estado PARCIAL o RECIBIDO (estado actual: " + estadoOC + ").");
                }

                String obs = "Anulación de la recepción " + numeroRecepcion + " de la OC " + numeroOC + ". Motivo: " + motivo;

                ps = conn.prepareStatement(
                    "SELECT rd.id, rd.id_detalle_compra, rd.cantidad, rd.id_almacen, rd.id_equipo, d.tipo_item, d.id_insumo, " +
                    "       d.cantidad_recibida, d.precio_unitario, COALESCE(i.descripcion, ib.descripcion) AS descripcion " +
                    "FROM recepciones_oc_detalle rd JOIN detalle_compra d ON d.id_detalle_compra = rd.id_detalle_compra " +
                    "LEFT JOIN insumos i ON i.id_insumo = d.id_insumo LEFT JOIN equipos e ON e.id_equipo = d.id_equipo " +
                    "LEFT JOIN insumos ib ON ib.id_insumo = e.id_insumo_base " +
                    "WHERE rd.id_recepcion = ? ORDER BY rd.id FOR UPDATE OF d");
                ps.setInt(1, idRecepcion);
                rs = ps.executeQuery();
                List<JSONObject> lineas = new ArrayList<JSONObject>();
                while (rs.next()) {
                    JSONObject l = new JSONObject();
                    l.put("id", rs.getInt("id"));
                    l.put("id_detalle", rs.getInt("id_detalle_compra"));
                    l.put("cantidad", rs.getInt("cantidad"));
                    l.put("id_almacen", rs.getInt("id_almacen"));
                    l.put("id_equipo", rs.getInt("id_equipo"));
                    l.put("tipo", rs.getString("tipo_item"));
                    l.put("id_insumo", rs.getInt("id_insumo"));
                    l.put("recibida", rs.getInt("cantidad_recibida"));
                    l.put("precio", rs.getBigDecimal("precio_unitario").toPlainString());
                    l.put("descripcion", rs.getString("descripcion") == null ? "" : rs.getString("descripcion"));
                    lineas.add(l);
                }
                rs.close(); ps.close(); rs = null; ps = null;
                if (lineas.isEmpty()) {
                    throw new IllegalArgumentException("La recepción no tiene artículos.");
                }

                int insumos = 0, activos = 0;
                for (JSONObject l : lineas) {
                    int cantidad = l.getInt("cantidad");
                    int idDetalle = l.getInt("id_detalle");
                    String item = l.getString("descripcion");
                    if (l.getInt("recibida") < cantidad) {
                        throw new IllegalArgumentException("La línea de " + item + " tiene recibido " + l.getInt("recibida")
                                + " y la recepción indica " + cantidad + ". Revise la OC antes de anular.");
                    }

                    if ("EQUIPO".equals(l.getString("tipo"))) {
                        // ----- Activo: solo si sigue tal como ingresó (disponible, sin movimientos posteriores) -----
                        int idEquipo = l.getInt("id_equipo");
                        ps = conn.prepareStatement(
                            "SELECT serial_activo, estado, id_centro_actual, id_centro_costo_actual FROM equipos WHERE id_equipo = ? FOR UPDATE");
                        ps.setInt(1, idEquipo);
                        rs = ps.executeQuery();
                        if (!rs.next()) {
                            throw new IllegalArgumentException("No se encontró el activo de la línea " + item + ".");
                        }
                        String numeroActivo = rs.getString("serial_activo");
                        String estadoEquipo = rs.getString("estado");
                        Integer centroActual = rs.getObject("id_centro_actual") == null ? null : Integer.valueOf(rs.getInt("id_centro_actual"));
                        Integer ccActual = rs.getObject("id_centro_costo_actual") == null ? null : Integer.valueOf(rs.getInt("id_centro_costo_actual"));
                        rs.close(); ps.close(); rs = null; ps = null;

                        if (!"DISPONIBLE".equals(estadoEquipo)) {
                            throw new IllegalArgumentException("El activo " + numeroActivo + " (" + item + ") ya no está disponible (estado "
                                    + estadoEquipo + "). Primero revierta lo que se hizo con él.");
                        }
                        ps = conn.prepareStatement(
                            "SELECT (SELECT COUNT(*) FROM movimientos_activos WHERE id_equipo = ? AND fecha_movimiento > ?) + " +
                            "       (SELECT COUNT(*) FROM solicitudes_activos_detalle sd JOIN solicitudes_activos s ON s.id_solicitud = sd.id_solicitud " +
                            "         WHERE sd.id_equipo = ? AND s.estado = 'PENDIENTE')");
                        ps.setInt(1, idEquipo);
                        ps.setTimestamp(2, fechaRecepcion);
                        ps.setInt(3, idEquipo);
                        rs = ps.executeQuery();
                        rs.next();
                        boolean tuvoMovimientos = rs.getInt(1) > 0;
                        rs.close(); ps.close(); rs = null; ps = null;
                        if (tuvoMovimientos) {
                            throw new IllegalArgumentException("El activo " + numeroActivo + " (" + item + ") ya tuvo movimientos o tiene una solicitud pendiente "
                                    + "después de recibirse. Primero revierta esos movimientos.");
                        }

                        Movimiento m = new Movimiento(Movimiento.ANULACION_RECEPCION, idEquipo, idUsuarioSesion);
                        m.centroOrigen = centroActual;
                        m.centroCostoOrigen = ccActual;
                        m.estadoAnterior = "DISPONIBLE";
                        m.estadoNuevo = "SOLICITADO";
                        m.observaciones = obs + " Número de activo liberado: " + numeroActivo + ".";
                        m.registrar(conn);

                        // Vuelve a ser un pedido pendiente de la OC (sin número de activo ni ubicación)
                        ps = conn.prepareStatement(
                            "UPDATE equipos SET estado = 'SOLICITADO', serial_activo = ?, numero_serie = NULL, fecha_ingreso = NULL, " +
                            "id_centro_actual = NULL, id_centro_costo_actual = NULL, id_almacen_actual = NULL WHERE id_equipo = ?");
                        ps.setString(1, Seriales.temporal(idUsuarioSesion, idEquipo));
                        ps.setInt(2, idEquipo);
                        ps.executeUpdate();
                        ps.close(); ps = null;

                        ps = conn.prepareStatement(
                            "UPDATE detalle_compra SET cantidad_recibida = cantidad_recibida - ?, id_centro_asignado = NULL, " +
                            "id_centro_costo_asignado = NULL, id_equipo_generado = NULL WHERE id_detalle_compra = ?");
                        ps.setInt(1, cantidad);
                        ps.setInt(2, idDetalle);
                        ps.executeUpdate();
                        ps.close(); ps = null;
                        activos++;
                    } else {
                        // ----- Insumo: sale del stock del almacén donde entró -----
                        int idAlmacen = l.getInt("id_almacen");
                        if (idAlmacen <= 0) {
                            idAlmacen = almacenesElegidos.optInt(String.valueOf(l.getInt("id")), 0);
                            if (idAlmacen <= 0) {
                                throw new IllegalArgumentException("Indique de qué almacén se descuenta " + item
                                        + " (la recepción es anterior al registro de almacenes).");
                            }
                            ps = conn.prepareStatement("SELECT 1 FROM almacenes WHERE id_almacen = ? AND id_centro = ?");
                            ps.setInt(1, idAlmacen);
                            ps.setInt(2, idCentroDestino);
                            rs = ps.executeQuery();
                            boolean valido = rs.next();
                            rs.close(); ps.close(); rs = null; ps = null;
                            if (!valido) {
                                throw new IllegalArgumentException("El almacén indicado para " + item + " no pertenece al centro destino de la OC.");
                            }
                            ps = conn.prepareStatement("UPDATE recepciones_oc_detalle SET id_almacen = ? WHERE id = ?");
                            ps.setInt(1, idAlmacen);
                            ps.setInt(2, l.getInt("id"));
                            ps.executeUpdate();
                            ps.close(); ps = null;
                        }

                        // Si ya se usó parte del stock, MovimientoStock lo impide y explica cuánto hay
                        MovimientoStock salida = new MovimientoStock(MovimientoStock.ANULACION_RECEPCION, l.getInt("id_insumo"), idAlmacen, -cantidad, idUsuarioSesion);
                        salida.documento = numeroRecepcion;
                        salida.idCompra = Integer.valueOf(idCompra);
                        salida.costoUnitario = new BigDecimal(l.getString("precio"));
                        salida.observacion = obs;
                        salida.registrar(conn);

                        ps = conn.prepareStatement("UPDATE detalle_compra SET cantidad_recibida = cantidad_recibida - ? WHERE id_detalle_compra = ?");
                        ps.setInt(1, cantidad);
                        ps.setInt(2, idDetalle);
                        ps.executeUpdate();
                        ps.close(); ps = null;
                        insumos++;
                    }
                }

                ps = conn.prepareStatement(
                    "UPDATE recepciones_oc SET anulada = true, fecha_anulacion = CURRENT_TIMESTAMP, id_usuario_anula = ?, motivo_anulacion = ? " +
                    "WHERE id_recepcion = ?");
                ps.setInt(1, idUsuarioSesion);
                ps.setString(2, motivo);
                ps.setInt(3, idRecepcion);
                ps.executeUpdate();
                ps.close(); ps = null;

                String nuevoEstado = recalcularEstado(conn, idCompra);
                conn.commit();
                out.print("<div class='alert alert-success'>Recepción " + Html.esc(numeroRecepcion) + " anulada. "
                        + (insumos > 0 ? "Se descontó del stock lo recibido de " + insumos + (insumos == 1 ? " insumo" : " insumos") + ". " : "")
                        + (activos > 0 ? activos + (activos == 1 ? " activo volvió" : " activos volvieron") + " a SOLICITADO. " : "")
                        + "La OC quedó en estado <strong>" + Html.esc(nuevoEstado) + "</strong>.</div>");
            } catch (IllegalArgumentException e) {
                conn.rollback();
                out.print("<div class='alert alert-danger'>" + Html.esc(Errores.mensaje(application, e)) + "</div>");
            } catch (Exception e) {
                conn.rollback();
                application.log("Error al anular la recepción " + idRecepcion, e);
                out.print("<div class='alert alert-danger'>No se pudo anular la recepción. Intente de nuevo.</div>");
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // ---------- OBTENER DETALLE COMPLETO (tabla editable) ----------
        else if ("obtenerDetalle".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id"));
            out.print(detalleOC(conn, idCompra).toString());
        }

        // ---------- ACTUALIZAR CABECERA DE OC ----------
        else if ("actualizarOC".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id_compra"));
            String idProvStr = request.getParameter("id_proveedor");
            String idCentStr = request.getParameter("id_centro_destino");
            String observaciones = request.getParameter("observaciones");

            if (idProvStr == null || idProvStr.trim().isEmpty() || idCentStr == null || idCentStr.trim().isEmpty()) {
                out.print("<div class='alert alert-danger'>Seleccione proveedor y centro destino</div>");
                return;
            }
            int idProveedor = Integer.parseInt(idProvStr.trim());
            int idCentro = Integer.parseInt(idCentStr.trim());

            String estado = estadoOC(conn, idCompra);
            if (estado == null) {
                out.print("<div class='alert alert-danger'>OC no encontrada</div>");
                return;
            }
            if (!editable(estado)) {
                out.print("<div class='alert alert-danger'>No se puede modificar una OC en estado " + Html.esc(estado) + "</div>");
                return;
            }

            ps = conn.prepareStatement("UPDATE compras SET id_proveedor=?, id_centro_destino=?, observaciones=? WHERE id_compra=?");
            ps.setInt(1, idProveedor);
            ps.setInt(2, idCentro);
            ps.setString(3, observaciones);
            ps.setInt(4, idCompra);
            ps.executeUpdate();
            out.print("<div class='alert alert-success'>OC actualizada correctamente</div>");
        }

        // ---------- ACTUALIZAR DETALLE (cantidad y precio) ----------
        else if ("actualizarDetalleOC".equals(tipo)) {
            int idDetalle = Integer.parseInt(request.getParameter("id_detalle"));
            int cantidad = Integer.parseInt(request.getParameter("cantidad").trim());
            BigDecimal precio = new BigDecimal(request.getParameter("precio").trim());

            if (cantidad <= 0) {
                out.print("error: La cantidad debe ser mayor a 0");
                return;
            }
            if (precio.compareTo(BigDecimal.ZERO) < 0) {
                out.print("error: El precio no puede ser negativo");
                return;
            }

            ps = conn.prepareStatement(
                "SELECT c.id_compra, c.estado, d.tipo_item, d.cantidad_recibida FROM detalle_compra d " +
                "INNER JOIN compras c ON d.id_compra = c.id_compra WHERE d.id_detalle_compra = ?"
            );
            ps.setInt(1, idDetalle);
            rs = ps.executeQuery();
            if (!rs.next()) {
                out.print("error: Detalle no encontrado");
                return;
            }
            int idCompra = rs.getInt("id_compra");
            String estado = rs.getString("estado");
            String tipoItem = rs.getString("tipo_item");
            int recibida = rs.getInt("cantidad_recibida");
            rs.close(); ps.close(); rs = null; ps = null;

            if (!editable(estado)) {
                out.print("error: No se puede modificar, OC " + Html.esc(estado));
                return;
            }
            if ("EQUIPO".equals(tipoItem) && cantidad != 1) {
                out.print("error: Cada equipo es una unidad; para pedir más agregue otra línea");
                return;
            }
            if (cantidad < recibida) {
                out.print("error: La cantidad no puede ser menor a lo ya recibido (" + recibida + ")");
                return;
            }

            conn.setAutoCommit(false);
            try {
                ps = conn.prepareStatement("UPDATE detalle_compra SET cantidad=?, precio_unitario=? WHERE id_detalle_compra=?");
                ps.setInt(1, cantidad);
                ps.setBigDecimal(2, precio);
                ps.setInt(3, idDetalle);
                ps.executeUpdate();
                ps.close(); ps = null;
                recalcularEstado(conn, idCompra);
                conn.commit();
                out.print("ok");
            } catch (Exception e) {
                conn.rollback();
                out.print("error: " + Html.esc(Errores.mensaje(application, e)));
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // ---------- ELIMINAR DETALLE ----------
        else if ("eliminarDetalleOC".equals(tipo)) {
            int idDetalle = Integer.parseInt(request.getParameter("id"));

            ps = conn.prepareStatement(
                "SELECT c.id_compra, c.estado, d.tipo_item, d.id_equipo, d.cantidad_recibida, " +
                "(SELECT COUNT(*) FROM detalle_compra x WHERE x.id_compra = c.id_compra) AS lineas " +
                "FROM detalle_compra d INNER JOIN compras c ON d.id_compra = c.id_compra WHERE d.id_detalle_compra = ?"
            );
            ps.setInt(1, idDetalle);
            rs = ps.executeQuery();
            if (!rs.next()) {
                out.print("error: Detalle no encontrado");
                return;
            }
            int idCompra = rs.getInt("id_compra");
            String estado = rs.getString("estado");
            String tipoItem = rs.getString("tipo_item");
            int idEquipo = rs.getInt("id_equipo");
            int recibida = rs.getInt("cantidad_recibida");
            int lineas = rs.getInt("lineas");
            rs.close(); ps.close(); rs = null; ps = null;

            if (!editable(estado)) {
                out.print("error: No se puede eliminar, OC " + Html.esc(estado));
                return;
            }
            if (recibida > 0) {
                out.print("error: No se puede eliminar un artículo que ya tiene unidades recibidas");
                return;
            }
            ps = conn.prepareStatement("SELECT 1 FROM recepciones_oc_detalle WHERE id_detalle_compra = ? LIMIT 1");
            ps.setInt(1, idDetalle);
            rs = ps.executeQuery();
            boolean conHistorial = rs.next();
            rs.close(); ps.close(); rs = null; ps = null;
            if (conHistorial) {
                out.print("error: No se puede eliminar: el artículo tiene recepciones (aunque estén anuladas) en su historial. Ajuste la cantidad en su lugar");
                return;
            }
            if (lineas <= 1) {
                out.print("error: La OC debe tener al menos un artículo. Si ya no se necesita, anúlela");
                return;
            }

            conn.setAutoCommit(false);
            try {
                ps = conn.prepareStatement("DELETE FROM detalle_compra WHERE id_detalle_compra=?");
                ps.setInt(1, idDetalle);
                ps.executeUpdate();
                ps.close(); ps = null;

                if ("EQUIPO".equals(tipoItem)) {
                    ps = conn.prepareStatement("DELETE FROM equipos WHERE id_equipo = ? AND estado = 'SOLICITADO'");
                    ps.setInt(1, idEquipo);
                    ps.executeUpdate();
                    ps.close(); ps = null;
                }
                recalcularEstado(conn, idCompra);
                conn.commit();
                out.print("Artículo eliminado con éxito");
            } catch (Exception e) {
                conn.rollback();
                out.print("error: " + Html.esc(Errores.mensaje(application, e)));
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // ---------- AGREGAR DETALLE (insumo) ----------
        else if ("agregarDetalleOC".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id_compra"));
            int idInsumo = Integer.parseInt(request.getParameter("id_insumo"));
            int cantidad = Integer.parseInt(request.getParameter("cantidad").trim());
            BigDecimal precio = new BigDecimal(request.getParameter("precio").trim());

            if (cantidad <= 0) {
                out.print("<div class='alert alert-danger'>La cantidad debe ser mayor a 0</div>");
                return;
            }
            if (precio.compareTo(BigDecimal.ZERO) < 0) {
                out.print("<div class='alert alert-danger'>El precio no puede ser negativo</div>");
                return;
            }

            String estado = estadoOC(conn, idCompra);
            if (estado == null) {
                out.print("<div class='alert alert-danger'>OC no encontrada</div>");
                return;
            }
            if (!editable(estado)) {
                out.print("<div class='alert alert-danger'>No se puede agregar, OC " + Html.esc(estado) + "</div>");
                return;
            }

            conn.setAutoCommit(false);
            try {
                ps = conn.prepareStatement(
                    "INSERT INTO detalle_compra (id_compra, tipo_item, id_insumo, cantidad, cantidad_recibida, precio_unitario) " +
                    "VALUES (?, 'INSUMO', ?, ?, 0, ?)"
                );
                ps.setInt(1, idCompra);
                ps.setInt(2, idInsumo);
                ps.setInt(3, cantidad);
                ps.setBigDecimal(4, precio);
                ps.executeUpdate();
                ps.close(); ps = null;
                recalcularEstado(conn, idCompra);
                conn.commit();
                out.print("Artículo agregado con éxito");
            } catch (Exception e) {
                conn.rollback();
                out.print("<div class='alert alert-danger'>Error: " + Html.esc(Errores.mensaje(application, e)) + "</div>");
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // ---------- ANULAR OC ----------
        else if ("anularOC".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id"));

            String estado = estadoOC(conn, idCompra);
            if (estado == null) {
                out.print("<div class='alert alert-danger'>OC no encontrada</div>");
                return;
            }
            if ("ANULADO".equals(estado)) {
                out.print("<div class='alert alert-warning'>Esta OC ya está anulada</div>");
                return;
            }
            if ("RECIBIDO".equals(estado)) {
                out.print("<div class='alert alert-danger'>No se puede anular una OC recibida</div>");
                return;
            }

            conn.setAutoCommit(false);
            try {
                ps = conn.prepareStatement("UPDATE compras SET estado='ANULADO' WHERE id_compra=?");
                ps.setInt(1, idCompra);
                ps.executeUpdate();
                ps.close(); ps = null;

                // Los equipos pedidos que nunca llegaron quedan anulados (no aparecen como activos)
                ps = conn.prepareStatement(
                    "UPDATE equipos SET estado='ANULADO', activo=false " +
                    "WHERE estado='SOLICITADO' AND id_equipo IN " +
                    "(SELECT id_equipo FROM detalle_compra WHERE id_compra=? AND tipo_item='EQUIPO')");
                ps.setInt(1, idCompra);
                ps.executeUpdate();
                ps.close(); ps = null;

                conn.commit();
                out.print("<div class='alert alert-success'>OC anulada correctamente</div>");
            } catch (Exception e) {
                conn.rollback();
                out.print("<div class='alert alert-danger'>Error al anular: " + Html.esc(Errores.mensaje(application, e)) + "</div>");
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // ============================================================
        // RECEPCIÓN DE OC (insumos y equipos)
        // ============================================================

        // ---------- LISTAR OC PENDIENTES (SOLICITADO o PARCIAL) ----------
        else if ("listarOCPendientes".equals(tipo)) {
            String sql = "SELECT id_compra, numero_oc FROM compras WHERE estado IN ('SOLICITADO', 'PARCIAL') AND numero_oc IS NOT NULL AND "
                    + permisos.sqlCentros("id_centro_destino") + " ORDER BY numero_oc";
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            out.print("<option value=''>-- Seleccionar --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt("id_compra") + "'>" + Html.esc(rs.getString("numero_oc")) + "</option>");
            }
        }

        // ---------- LISTAR CENTROS DE COSTO POR CENTRO (ubicación de los activos) ----------
        else if ("listarCentrosCostoPorCentro".equals(tipo)) {
            int idCentro = Integer.parseInt(request.getParameter("id_centro"));
            permisos.exigirCentro(idCentro);
            StringBuilder html = new StringBuilder("<option value=''>-- Seleccionar --</option>");
            ps = conn.prepareStatement("SELECT id_centro_costo, codigo, descripcion FROM centros_costo WHERE id_centro = ? AND activo = true ORDER BY codigo");
            ps.setInt(1, idCentro);
            rs = ps.executeQuery();
            while (rs.next()) {
                String d = rs.getString("descripcion");
                html.append("<option value='").append(rs.getInt("id_centro_costo")).append("'>")
                    .append(Html.esc(rs.getString("codigo"))).append(d == null || d.trim().isEmpty() ? "" : " - " + Html.esc(d))
                    .append("</option>");
            }
            out.print(html.toString());
        }

        // ---------- LISTAR ALMACENES POR CENTRO ----------
        else if ("listarAlmacenesPorCentro".equals(tipo)) {
            int idCentro = Integer.parseInt(request.getParameter("id_centro"));
            permisos.exigirCentro(idCentro);
            StringBuilder html = new StringBuilder("<option value=''>-- Seleccionar --</option>");
            ps = conn.prepareStatement("SELECT id_almacen, nombre FROM almacenes WHERE id_centro = ? AND activo=true ORDER BY nombre");
            ps.setInt(1, idCentro);
            rs = ps.executeQuery();
            while (rs.next()) {
                html.append("<option value='").append(rs.getInt("id_almacen")).append("'>").append(Html.esc(rs.getString("nombre"))).append("</option>");
            }
            out.print(html.toString());
        }

        // ---------- OBTENER DETALLE PARA RECEPCIÓN (insumos y equipos pendientes) ----------
        else if ("obtenerDetalleRecepcion".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id"));
            JSONObject data = new JSONObject();

            ps = conn.prepareStatement("SELECT numero_oc, estado, id_centro_destino FROM compras WHERE id_compra = ?");
            ps.setInt(1, idCompra);
            rs = ps.executeQuery();
            if (rs.next()) {
                data.put("numero_oc", rs.getString("numero_oc"));
                data.put("estado", rs.getString("estado"));
                data.put("id_centro_destino", rs.getInt("id_centro_destino"));
                if (!permisos.veCentro(rs.getInt("id_centro_destino"))) {
                    data = new JSONObject();
                    data.put("error", "La OC es de un centro al que no tiene acceso");
                    out.print(data.toString());
                    return;
                }
            } else {
                data.put("error", "OC no encontrada");
                out.print(data.toString());
                return;
            }
            rs.close(); ps.close(); rs = null; ps = null;

            JSONArray detalle = new JSONArray();
            ps = conn.prepareStatement(
                "SELECT d.id_detalle_compra, d.tipo_item, i.codigo, i.descripcion, d.cantidad, d.cantidad_recibida, " +
                "(d.cantidad - d.cantidad_recibida) AS pendiente " +
                "FROM detalle_compra d " +
                "LEFT JOIN equipos e ON d.id_equipo = e.id_equipo " +
                "INNER JOIN insumos i ON i.id_insumo = COALESCE(d.id_insumo, e.id_insumo_base) " +
                "WHERE d.id_compra = ? AND d.cantidad > d.cantidad_recibida " +
                "ORDER BY d.tipo_item DESC, d.id_detalle_compra"
            );
            ps.setInt(1, idCompra);
            rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("id_detalle", rs.getInt("id_detalle_compra"));
                item.put("tipo_item", rs.getString("tipo_item"));
                item.put("codigo", rs.getString("codigo"));
                item.put("descripcion", rs.getString("descripcion"));
                item.put("cantidad", rs.getInt("cantidad"));
                item.put("recibido", rs.getInt("cantidad_recibida"));
                item.put("pendiente", rs.getInt("pendiente"));
                detalle.put(item);
            }
            data.put("detalle", detalle);
            out.print(data.toString());
        }

        // ---------- PROCESAR RECEPCIÓN ----------
        // datos = [{id_detalle, cantidad, id_almacen, serial}]  (serial solo para equipos)
        // cantidad 0 = esa línea no se recibe ahora.
        else if ("procesarRecepcion".equals(tipo)) {
            int idCompra = Integer.parseInt(request.getParameter("id_compra"));
            JSONArray datos = new JSONArray(request.getParameter("datos"));

            conn.setAutoCommit(false);
            try {
                // Bloquea la OC mientras se procesa (dos recepciones simultáneas no se pisan)
                ps = conn.prepareStatement("SELECT estado, id_centro_destino, numero_oc FROM compras WHERE id_compra = ? FOR UPDATE");
                ps.setInt(1, idCompra);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("OC no encontrada");
                }
                String estado = rs.getString("estado");
                int idCentroDestino = rs.getInt("id_centro_destino");
                permisos.exigirCentro(idCentroDestino);
                String numeroOC = rs.getString("numero_oc");
                rs.close(); ps.close(); rs = null; ps = null;
                int idUsuarioSesion = ((Integer) session.getAttribute("id_usuario")).intValue();

                if (!"SOLICITADO".equals(estado) && !"PARCIAL".equals(estado)) {
                    throw new IllegalArgumentException("La OC no está en estado SOLICITADO o PARCIAL");
                }

                // Registro de esta recepción (numerada por OC: OC-...-R01, R02...). La OC está bloqueada.
                ps = conn.prepareStatement("SELECT COUNT(*) + 1 FROM recepciones_oc WHERE id_compra = ?");
                ps.setInt(1, idCompra);
                rs = ps.executeQuery();
                rs.next();
                int nroRecepcion = rs.getInt(1);
                rs.close(); ps.close(); rs = null; ps = null;
                String numeroRecepcion = (numeroOC == null ? "OC-" + idCompra : numeroOC) + "-R" + (nroRecepcion < 10 ? "0" : "") + nroRecepcion;
                ps = conn.prepareStatement("INSERT INTO recepciones_oc (id_compra, numero, id_usuario) VALUES (?, ?, ?) RETURNING id_recepcion");
                ps.setInt(1, idCompra);
                ps.setString(2, numeroRecepcion);
                ps.setInt(3, idUsuarioSesion);
                rs = ps.executeQuery();
                rs.next();
                int idRecepcion = rs.getInt(1);
                rs.close(); ps.close(); rs = null; ps = null;

                int lineasRecibidas = 0;
                Set<String> seriesEnEstaRecepcion = new HashSet<String>();
                List<String> activosGenerados = new ArrayList<String>();

                for (int i = 0; i < datos.length(); i++) {
                    JSONObject obj = datos.getJSONObject(i);
                    int idDetalle = obj.getInt("id_detalle");
                    int cantidadRecibir = obj.getInt("cantidad");

                    if (cantidadRecibir < 0) {
                        throw new IllegalArgumentException("La cantidad a recibir no puede ser negativa");
                    }
                    if (cantidadRecibir == 0) {
                        continue;   // esta línea no se recibe ahora
                    }
                    // El detalle debe pertenecer a ESTA OC
                    ps = conn.prepareStatement(
                        "SELECT d.tipo_item, d.id_insumo, d.id_equipo, d.cantidad, d.cantidad_recibida, d.precio_unitario, i.descripcion " +
                        "FROM detalle_compra d " +
                        "LEFT JOIN equipos e ON e.id_equipo = d.id_equipo " +
                        "LEFT JOIN insumos i ON i.id_insumo = COALESCE(d.id_insumo, e.id_insumo_base) " +
                        "WHERE d.id_detalle_compra = ? AND d.id_compra = ? FOR UPDATE OF d");
                    ps.setInt(1, idDetalle);
                    ps.setInt(2, idCompra);
                    rs = ps.executeQuery();
                    if (!rs.next()) {
                        throw new IllegalArgumentException("El detalle " + idDetalle + " no pertenece a esta OC");
                    }
                    String tipoItem = rs.getString("tipo_item");
                    int idInsumo = rs.getInt("id_insumo");
                    int idEquipo = rs.getInt("id_equipo");
                    String descripcionItem = rs.getString("descripcion");
                    BigDecimal precioCompra = rs.getBigDecimal("precio_unitario");
                    int pendiente = rs.getInt("cantidad") - rs.getInt("cantidad_recibida");
                    rs.close(); ps.close(); rs = null; ps = null;

                    if (cantidadRecibir > pendiente) {
                        throw new IllegalArgumentException("Cantidad a recibir excede lo pendiente para el detalle " + idDetalle);
                    }

                    if ("EQUIPO".equals(tipoItem)) {
                        // ----- Activo fijo: se ubica en un CENTRO DE COSTO (no en un almacén) -----
                        int idCentroCosto = obj.optInt("id_centro_costo", 0);
                        if (idCentroCosto <= 0) {
                            throw new IllegalArgumentException("Seleccione el centro de costo de todos los activos que va a recibir");
                        }
                        ps = conn.prepareStatement("SELECT 1 FROM centros_costo WHERE id_centro_costo = ? AND id_centro = ? AND activo = true");
                        ps.setInt(1, idCentroCosto);
                        ps.setInt(2, idCentroDestino);
                        rs = ps.executeQuery();
                        boolean ccValido = rs.next();
                        rs.close(); ps.close(); rs = null; ps = null;
                        if (!ccValido) {
                            throw new IllegalArgumentException("El centro de costo seleccionado no pertenece al centro destino de la OC");
                        }

                        // Número de serie del fabricante: OPCIONAL, pero si se carga no se puede repetir
                        String serie = obj.optString("serie", "").trim();
                        String serieGuardar = null;
                        if (!serie.isEmpty()) {
                            if (serie.length() > 100) {
                                throw new IllegalArgumentException("El número de serie no puede superar 100 caracteres");
                            }
                            if (!seriesEnEstaRecepcion.add(serie.toUpperCase())) {
                                throw new IllegalArgumentException("El número de serie " + serie + " está repetido");
                            }
                            ps = conn.prepareStatement("SELECT 1 FROM equipos WHERE UPPER(numero_serie) = UPPER(?) AND id_equipo <> ?");
                            ps.setString(1, serie);
                            ps.setInt(2, idEquipo);
                            rs = ps.executeQuery();
                            boolean duplicado = rs.next();
                            rs.close(); ps.close(); rs = null; ps = null;
                            if (duplicado) {
                                throw new IllegalArgumentException("Ya existe un activo con el número de serie " + serie);
                            }
                            serieGuardar = serie;
                        }

                        // Número de activo / patrimonio: lo genera el sistema (único e irrepetible)
                        ps = conn.prepareStatement("SELECT 'AF-' || lpad(nextval('public.seq_numero_activo')::text, 6, '0')");
                        rs = ps.executeQuery();
                        rs.next();
                        String numeroActivo = rs.getString(1);
                        rs.close(); ps.close(); rs = null; ps = null;

                        ps = conn.prepareStatement(
                            "UPDATE equipos SET serial_activo = ?, numero_serie = ?, estado = 'DISPONIBLE', fecha_ingreso = CURRENT_DATE, " +
                            "id_centro_actual = ?, id_centro_costo_actual = ?, id_almacen_actual = NULL, activo = true WHERE id_equipo = ?");
                        ps.setString(1, numeroActivo);
                        if (serieGuardar == null) { ps.setNull(2, Types.VARCHAR); } else { ps.setString(2, serieGuardar); }
                        ps.setInt(3, idCentroDestino);
                        ps.setInt(4, idCentroCosto);
                        ps.setInt(5, idEquipo);
                        ps.executeUpdate();
                        ps.close(); ps = null;

                        ps = conn.prepareStatement(
                            "UPDATE detalle_compra SET cantidad_recibida = cantidad_recibida + ?, " +
                            "id_centro_asignado = ?, id_centro_costo_asignado = ?, id_equipo_generado = ? WHERE id_detalle_compra = ?");
                        ps.setInt(1, cantidadRecibir);
                        ps.setInt(2, idCentroDestino);
                        ps.setInt(3, idCentroCosto);
                        ps.setInt(4, idEquipo);
                        ps.setInt(5, idDetalle);
                        ps.executeUpdate();
                        ps.close(); ps = null;

                        // Primer evento del historial del activo: su INGRESO por compra
                        Movimiento ingreso = new Movimiento(Movimiento.INGRESO, idEquipo, idUsuarioSesion);
                        ingreso.centroDestino = Integer.valueOf(idCentroDestino);
                        ingreso.centroCostoDestino = Integer.valueOf(idCentroCosto);
                        ingreso.estadoAnterior = "SOLICITADO";
                        ingreso.estadoNuevo = "DISPONIBLE";
                        ingreso.observaciones = "Ingreso por compra - OC " + numeroOC
                                + (serieGuardar == null ? "" : " - Serie del fabricante: " + serieGuardar);
                        ingreso.registrar(conn);

                        ps = conn.prepareStatement(
                            "INSERT INTO recepciones_oc_detalle (id_recepcion, id_detalle_compra, cantidad, id_equipo, id_centro_costo, numero_activo) VALUES (?, ?, ?, ?, ?, ?)");
                        ps.setInt(1, idRecepcion);
                        ps.setInt(2, idDetalle);
                        ps.setInt(3, cantidadRecibir);
                        ps.setInt(4, idEquipo);
                        ps.setInt(5, idCentroCosto);
                        ps.setString(6, numeroActivo);
                        ps.executeUpdate();
                        ps.close(); ps = null;

                        activosGenerados.add(numeroActivo + " (" + (descripcionItem == null ? "" : descripcionItem) + ")");
                    } else {
                        // ----- Insumo: suma al stock del almacén -----
                        int idAlmacen = obj.optInt("id_almacen", 0);
                        if (idAlmacen <= 0) {
                            throw new IllegalArgumentException("Seleccione el almacén de todos los insumos que va a recibir");
                        }
                        ps = conn.prepareStatement("SELECT 1 FROM almacenes WHERE id_almacen = ? AND id_centro = ? AND activo = true");
                        ps.setInt(1, idAlmacen);
                        ps.setInt(2, idCentroDestino);
                        rs = ps.executeQuery();
                        boolean almacenValido = rs.next();
                        rs.close(); ps.close(); rs = null; ps = null;
                        if (!almacenValido) {
                            throw new IllegalArgumentException("El almacén seleccionado no pertenece al centro destino de la OC");
                        }

                        ps = conn.prepareStatement("UPDATE detalle_compra SET cantidad_recibida = cantidad_recibida + ?, id_almacen_destino = ? WHERE id_detalle_compra = ?");
                        ps.setInt(1, cantidadRecibir);
                        ps.setInt(2, idAlmacen);
                        ps.setInt(3, idDetalle);
                        ps.executeUpdate();
                        ps.close(); ps = null;

                        // Entrada al stock y al Kardex
                        MovimientoStock entrada = new MovimientoStock(MovimientoStock.ENTRADA_COMPRA, idInsumo, idAlmacen, cantidadRecibir, idUsuarioSesion);
                        entrada.documento = numeroOC;
                        entrada.idCompra = Integer.valueOf(idCompra);
                        entrada.costoUnitario = precioCompra;
                        entrada.observacion = "Recepción de compra " + numeroRecepcion;
                        entrada.registrar(conn);

                        ps = conn.prepareStatement(
                            "INSERT INTO recepciones_oc_detalle (id_recepcion, id_detalle_compra, cantidad, id_almacen) VALUES (?, ?, ?, ?)");
                        ps.setInt(1, idRecepcion);
                        ps.setInt(2, idDetalle);
                        ps.setInt(3, cantidadRecibir);
                        ps.setInt(4, idAlmacen);
                        ps.executeUpdate();
                        ps.close(); ps = null;
                    }
                    lineasRecibidas++;
                }

                if (lineasRecibidas == 0) {
                    throw new IllegalArgumentException("Indique al menos un artículo con cantidad a recibir mayor a 0");
                }

                // Estado según TODAS las líneas (insumos y equipos)
                String nuevoEstado = recalcularEstado(conn, idCompra);

                conn.commit();
                StringBuilder msg = new StringBuilder("<div class='alert alert-success'>Recepción " + Html.esc(numeroRecepcion) + " procesada correctamente. Estado actual: " + Html.esc(nuevoEstado));
                if (!activosGenerados.isEmpty()) {
                    msg.append("<br><strong>Números de activo generados:</strong><ul class='mb-0'>");
                    for (String act : activosGenerados) {
                        msg.append("<li>").append(Html.esc(act)).append("</li>");
                    }
                    msg.append("</ul>");
                }
                msg.append("</div>");
                out.print(msg.toString());
            } catch (IllegalArgumentException e) {
                conn.rollback();
                out.print("<div class='alert alert-danger'>" + Html.esc(Errores.mensaje(application, e)) + "</div>");
            } catch (Exception e) {
                conn.rollback();
                out.print("<div class='alert alert-danger'>Error al procesar la recepción: " + Html.esc(Errores.mensaje(application, e)) + "</div>");
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        }

        else {
            out.print("error: Operación no reconocida");
        }

    } catch (NumberFormatException e) {
        out.print("error: Dato numérico inválido");
    } catch (Exception e) {
        out.print("error: " + Html.esc(Errores.mensaje(application, e)));
        e.printStackTrace();
    } finally {
        if (rs != null) try { rs.close(); } catch (Exception e) {}
        if (ps != null) try { ps.close(); } catch (Exception e) {}
    }

    }


    // Colores de estado
    private static String getBadgeColor(String estado) {
        if ("SOLICITADO".equals(estado)) return "badge-primary";
        if ("PARCIAL".equals(estado)) return "badge-warning";
        if ("RECIBIDO".equals(estado)) return "badge-success";
        if ("ANULADO".equals(estado)) return "badge-danger";
        return "badge-secondary";
    }

    /** Una OC solo se edita mientras está SOLICITADO o PARCIAL. */
    private static boolean editable(String estado) {
        return "SOLICITADO".equals(estado) || "PARCIAL".equals(estado);
    }

    private static String estadoOC(Connection conn, int idCompra) throws SQLException {
        PreparedStatement p = conn.prepareStatement("SELECT estado FROM compras WHERE id_compra = ?");
        try {
            p.setInt(1, idCompra);
            ResultSet r = p.executeQuery();
            String estado = r.next() ? r.getString(1) : null;
            r.close();
            return estado;
        } finally {
            p.close();
        }
    }

    /**
     * Estado de una OC en curso según sus líneas (insumos Y equipos):
     * todo recibido = RECIBIDO, algo recibido = PARCIAL, nada = SOLICITADO.
     * No toca OCs en BORRADOR ni ANULADO. Desde RECIBIDO solo cambia al anular una recepción.
     */
    private static String recalcularEstado(Connection conn, int idCompra) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
            "SELECT COUNT(*) FILTER (WHERE cantidad > cantidad_recibida) AS pendientes, " +
            "COALESCE(SUM(cantidad_recibida), 0) AS recibidas FROM detalle_compra WHERE id_compra = ?");
        int pendientes;
        long recibidas;
        try {
            p.setInt(1, idCompra);
            ResultSet r = p.executeQuery();
            r.next();
            pendientes = r.getInt("pendientes");
            recibidas = r.getLong("recibidas");
            r.close();
        } finally {
            p.close();
        }
        String nuevo = (pendientes == 0) ? "RECIBIDO" : (recibidas > 0 ? "PARCIAL" : "SOLICITADO");
        PreparedStatement u = conn.prepareStatement(
            "UPDATE compras SET estado = ? WHERE id_compra = ? AND estado IN ('SOLICITADO', 'PARCIAL', 'RECIBIDO')");
        try {
            u.setString(1, nuevo);
            u.setInt(2, idCompra);
            u.executeUpdate();
        } finally {
            u.close();
        }
        return nuevo;
    }

    /** Detalle de la OC (insumos y equipos) para la edición. */
    private static JSONArray detalleOC(Connection conn, int idCompra) throws SQLException {
        JSONArray detalle = new JSONArray();
        PreparedStatement p = conn.prepareStatement(
            "SELECT d.id_detalle_compra, d.tipo_item, i.codigo, i.descripcion, d.precio_unitario, d.cantidad, " +
            "d.cantidad_recibida, (d.precio_unitario * d.cantidad) AS subtotal, e.serial_activo " +
            "FROM detalle_compra d " +
            "LEFT JOIN equipos e ON d.id_equipo = e.id_equipo " +
            "INNER JOIN insumos i ON i.id_insumo = COALESCE(d.id_insumo, e.id_insumo_base) " +
            "WHERE d.id_compra = ? ORDER BY d.id_detalle_compra");
        try {
            p.setInt(1, idCompra);
            ResultSet r = p.executeQuery();
            while (r.next()) {
                JSONObject item = new JSONObject();
                String tipoItem = r.getString("tipo_item");
                item.put("id_detalle", r.getInt("id_detalle_compra"));
                item.put("tipo_item", tipoItem);
                item.put("codigo", r.getString("codigo"));
                item.put("descripcion", r.getString("descripcion"));
                item.put("precio_unitario", r.getBigDecimal("precio_unitario").toPlainString());
                item.put("cantidad", r.getInt("cantidad"));
                item.put("recibido", r.getInt("cantidad_recibida"));
                item.put("subtotal", r.getBigDecimal("subtotal").toPlainString());
                String serial = r.getString("serial_activo");
                item.put("serial", ("EQUIPO".equals(tipoItem) && serial != null && !serial.startsWith("PEND-")) ? serial : "");
                detalle.put(item);
            }
            r.close();
        } finally {
            p.close();
        }
        return detalle;
    }

}
