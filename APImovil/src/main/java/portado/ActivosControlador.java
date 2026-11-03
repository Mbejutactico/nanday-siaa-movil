package portado;

import inventario.Movimiento;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
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
 * Copia de la web: Controlador/activosControlador.jsp
 * La lógica (validaciones, transacciones, stock y Kardex) es la misma que usa el sistema web.
 * Generado por portar.py; solo se quitaron las operaciones de impresión en PDF.
 */
@SuppressWarnings({"unused", "rawtypes"})
public final class ActivosControlador {

    private ActivosControlador() {
    }

    public static void atender(HttpServletRequest request, HttpServletResponse response, PrintWriter out, Connection conn,
                               Permisos permisos, SesionDatos session, ServletContext application) throws Exception {

    String tipo = request.getParameter("tipo");
    PreparedStatement ps = null;
    ResultSet rs = null;

    try {
        // ---------- RESUMEN: cantidades por estado ----------
        if ("resumen".equals(tipo)) {
            JSONObject res = new JSONObject();
            String[] estados = {"DISPONIBLE", "ASIGNADO", "EN_TRANSITO", "EN_REPARACION", "BAJA", "SOLICITADO"};
            for (int i = 0; i < estados.length; i++) {
                res.put(estados[i], 0);
            }
            int total = 0;
            ps = conn.prepareStatement(
                "SELECT e.estado, COUNT(*) AS cant FROM equipos e " +
                "LEFT JOIN detalle_compra d ON d.id_equipo = e.id_equipo " +
                "LEFT JOIN compras c ON c.id_compra = d.id_compra " +
                // los pedidos que siguen en un borrador de solicitud no cuentan todavía
                "WHERE e.estado <> 'ANULADO' AND NOT (e.estado = 'SOLICITADO' AND COALESCE(c.estado, 'BORRADOR') = 'BORRADOR') " +
                "AND " + permisos.sqlCentros("COALESCE(e.id_centro_actual, c.id_centro_destino)") + " " +
                "GROUP BY e.estado");
            rs = ps.executeQuery();
            while (rs.next()) {
                String estado = rs.getString("estado");
                int cant = rs.getInt("cant");
                res.put(estado, cant);
                if (!"SOLICITADO".equals(estado) && !"BAJA".equals(estado)) {
                    total += cant;
                }
            }
            rs.close(); ps.close(); rs = null; ps = null;
            res.put("TOTAL", total);

            ps = conn.prepareStatement(
                "SELECT COUNT(DISTINCT id_centro_costo_actual) FROM equipos " +
                "WHERE estado IN ('DISPONIBLE', 'ASIGNADO') AND id_centro_costo_actual IS NOT NULL AND " + permisos.sqlCentros("id_centro_actual"));
            rs = ps.executeQuery();
            rs.next();
            res.put("CENTROS_COSTO", rs.getInt(1));
            response.setContentType("application/json; charset=UTF-8");
            out.print(res.toString());
        }

        // ---------- CENTROS (filtro) ----------
        else if ("listarCentros".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_centro, nombre FROM centros WHERE " + permisos.sqlCentros("id_centro") + " ORDER BY nombre");
            rs = ps.executeQuery();
            out.print("<option value=''>Todos los centros</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt("id_centro") + "'>" + Html.esc(rs.getString("nombre")) + "</option>");
            }
        }

        // ---------- CENTROS DE COSTO ----------
        // filtro=true: todos (para filtrar); si no, solo activos (para reubicar).
        else if ("listarCentrosCosto".equals(tipo)) {
            int idCentro = numero(request.getParameter("id_centro"));
            boolean filtro = "true".equals(request.getParameter("filtro"));
            ps = conn.prepareStatement(
                "SELECT id_centro_costo, codigo, descripcion FROM centros_costo " +
                "WHERE (? = 0 OR id_centro = ?) AND " + permisos.sqlCentros("id_centro") + " " + (filtro ? "" : "AND activo = true ") +
                "ORDER BY codigo");
            ps.setInt(1, idCentro);
            ps.setInt(2, idCentro);
            rs = ps.executeQuery();
            out.print(filtro ? "<option value=''>Todos los centros de costo</option>" : "<option value=''>-- Seleccionar --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt("id_centro_costo") + "'>" + Html.esc(nombreCC(rs.getString("codigo"), rs.getString("descripcion"))) + "</option>");
            }
        }

        // ---------- USUARIOS ACTIVOS (responsables) ----------
        else if ("listarUsuarios".equals(tipo)) {
            ps = conn.prepareStatement("SELECT id_usuario, nombre, apellido FROM usuarios WHERE activo = true ORDER BY apellido, nombre");
            rs = ps.executeQuery();
            out.print("<option value=''>-- Persona externa (cargar datos abajo) --</option>");
            while (rs.next()) {
                out.print("<option value='" + rs.getInt("id_usuario") + "'>" + Html.esc(rs.getString("apellido")) + ", " + Html.esc(rs.getString("nombre")) + "</option>");
            }
        }

        // ---------- LISTAR ACTIVOS (JSON) ----------
        else if ("listar".equals(tipo)) {
            String estado = request.getParameter("estado");
            if (!estadoValido(estado)) {
                estado = "";
            }
            int idCentro = numero(request.getParameter("id_centro"));
            int idCentroCosto = numero(request.getParameter("id_centro_costo"));
            boolean pendientes = "true".equals(request.getParameter("pendientes"));

            ps = conn.prepareStatement(
                "SELECT e.id_equipo, e.serial_activo, e.numero_serie, e.estado, e.fecha_ingreso, " +
                "       i.codigo, i.descripcion, ce.nombre AS centro, cc.codigo AS cc_codigo, cc.descripcion AS cc_descripcion, " +
                "       c.numero_oc, " + SQL_RESPONSABLE + " AS responsable, " + SQL_SITUACION + " AS situacion " +
                "FROM equipos e " +
                "LEFT JOIN insumos i ON i.id_insumo = e.id_insumo_base " +
                "LEFT JOIN centros ce ON ce.id_centro = e.id_centro_actual " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = e.id_centro_costo_actual " +
                "LEFT JOIN detalle_compra d ON d.id_equipo = e.id_equipo " +
                "LEFT JOIN compras c ON c.id_compra = d.id_compra " +
                "WHERE (? = '' OR e.estado = ?) " +
                "  AND (? = 0 OR e.id_centro_actual = ?) " +
                "  AND (? = 0 OR e.id_centro_costo_actual = ?) " +
                "  AND (? OR e.estado NOT IN ('SOLICITADO', 'ANULADO')) " +
                "  AND " + permisos.sqlCentros("COALESCE(e.id_centro_actual, c.id_centro_destino)") + " " +
                "ORDER BY e.fecha_ingreso DESC NULLS LAST, e.id_equipo DESC");
            ps.setString(1, estado);
            ps.setString(2, estado);
            ps.setInt(3, idCentro);
            ps.setInt(4, idCentro);
            ps.setInt(5, idCentroCosto);
            ps.setInt(6, idCentroCosto);
            ps.setBoolean(7, pendientes);
            rs = ps.executeQuery();

            JSONArray lista = new JSONArray();
            while (rs.next()) {
                JSONObject a = new JSONObject();
                a.put("id", rs.getInt("id_equipo"));
                a.put("numero_activo", numeroActivo(rs.getString("serial_activo")));
                a.put("numero_serie", txt(rs.getString("numero_serie")));
                a.put("codigo", txt(rs.getString("codigo")));
                a.put("descripcion", txt(rs.getString("descripcion")));
                a.put("estado", txt(rs.getString("estado")));
                a.put("centro", txt(rs.getString("centro")));
                a.put("centro_costo", nombreCC(rs.getString("cc_codigo"), rs.getString("cc_descripcion")));
                a.put("responsable", "ASIGNADO".equals(rs.getString("estado")) ? txt(rs.getString("responsable")) : "");
                a.put("fecha_ingreso", rs.getDate("fecha_ingreso") == null ? "" : rs.getDate("fecha_ingreso").toString());
                a.put("numero_oc", txt(rs.getString("numero_oc")));
                a.put("situacion", txt(rs.getString("situacion")));
                lista.put(a);
            }
            response.setContentType("application/json; charset=UTF-8");
            out.print(lista.toString());
        }

        // ---------- FICHA + HISTORIAL COMPLETO DE UN ACTIVO (JSON) ----------
        else if ("detalle".equals(tipo)) {
            int idEquipo = Integer.parseInt(request.getParameter("id"));
            JSONObject f = new JSONObject();
            ps = conn.prepareStatement(
                "SELECT e.id_equipo, e.serial_activo, e.numero_serie, e.estado, e.fecha_ingreso, e.id_centro_actual, e.id_centro_costo_actual, " +
                "       i.codigo, i.descripcion, ce.nombre AS centro, cc.codigo AS cc_codigo, cc.descripcion AS cc_descripcion, " +
                "       c.numero_oc, c.fecha_compra, c.estado AS estado_oc, p.razon_social AS proveedor, p.ruc, " +
                "       d.precio_unitario, u.nombre || ' ' || u.apellido AS solicitante, " + SQL_RESPONSABLE + " AS responsable, " + SQL_SITUACION + " AS situacion " +
                "FROM equipos e " +
                "LEFT JOIN insumos i ON i.id_insumo = e.id_insumo_base " +
                "LEFT JOIN centros ce ON ce.id_centro = e.id_centro_actual " +
                "LEFT JOIN centros_costo cc ON cc.id_centro_costo = e.id_centro_costo_actual " +
                "LEFT JOIN detalle_compra d ON d.id_equipo = e.id_equipo " +
                "LEFT JOIN compras c ON c.id_compra = d.id_compra " +
                "LEFT JOIN proveedores p ON p.id_proveedor = c.id_proveedor " +
                "LEFT JOIN usuarios u ON u.id_usuario = c.id_usuario_solicitante " +
                "WHERE e.id_equipo = ?");
            ps.setInt(1, idEquipo);
            rs = ps.executeQuery();
            if (!rs.next()) {
                f.put("error", "Activo no encontrado");
                response.setContentType("application/json; charset=UTF-8");
                out.print(f.toString());
                return;
            }
            f.put("id", rs.getInt("id_equipo"));
            f.put("numero_activo", numeroActivo(rs.getString("serial_activo")));
            f.put("numero_serie", txt(rs.getString("numero_serie")));
            f.put("codigo", txt(rs.getString("codigo")));
            f.put("descripcion", txt(rs.getString("descripcion")));
            f.put("estado", txt(rs.getString("estado")));
            f.put("fecha_ingreso", rs.getDate("fecha_ingreso") == null ? "" : rs.getDate("fecha_ingreso").toString());
            f.put("id_centro", rs.getInt("id_centro_actual"));
            if (rs.getInt("id_centro_actual") != 0 && !permisos.veCentro(rs.getInt("id_centro_actual"))) {
                f = new JSONObject();
                f.put("error", "El activo es de un centro al que no tiene acceso");
                response.setContentType("application/json; charset=UTF-8");
                out.print(f.toString());
                return;
            }
            f.put("id_centro_costo", rs.getInt("id_centro_costo_actual"));
            f.put("centro", txt(rs.getString("centro")));
            f.put("centro_costo", nombreCC(rs.getString("cc_codigo"), rs.getString("cc_descripcion")));
            f.put("responsable", "ASIGNADO".equals(rs.getString("estado")) ? txt(rs.getString("responsable")) : "");
            f.put("situacion", txt(rs.getString("situacion")));
            f.put("numero_oc", txt(rs.getString("numero_oc")));
            f.put("fecha_compra", rs.getDate("fecha_compra") == null ? "" : rs.getDate("fecha_compra").toString());
            f.put("estado_oc", txt(rs.getString("estado_oc")));
            f.put("proveedor", txt(rs.getString("proveedor")));
            f.put("ruc", txt(rs.getString("ruc")));
            f.put("solicitante", txt(rs.getString("solicitante")));
            BigDecimal precio = rs.getBigDecimal("precio_unitario");
            f.put("valor", precio == null ? "" : precio.toPlainString());
            rs.close(); ps.close(); rs = null; ps = null;

            // Historial completo, del más reciente al más antiguo
            JSONArray movs = new JSONArray();
            ps = conn.prepareStatement(
                "SELECT m.id_movimiento, m.fecha_movimiento, m.tipo_movimiento, m.numero_movimiento_unico, " +
                "       m.estado_anterior, m.estado_nuevo, m.observaciones, m.justificacion_rechazo, " +
                "       m.datos_receptor_nombre, m.datos_receptor_apellido, m.datos_receptor_ci, " +
                "       m.datos_receptor_vehiculo_chapa, m.datos_receptor_vehiculo_marca, m.datos_receptor_vehiculo_modelo, " +
                "       co.nombre AS centro_origen, cd.nombre AS centro_destino, " +
                "       cco.codigo AS cco_codigo, cco.descripcion AS cco_descripcion, " +
                "       ccd.codigo AS ccd_codigo, ccd.descripcion AS ccd_descripcion, " +
                "       ao.nombre AS almacen_origen, ad.nombre AS almacen_destino, " +
                "       ug.nombre || ' ' || ug.apellido AS registrado_por, " +
                "       ue.nombre || ' ' || ue.apellido AS emisor, " +
                "       ur.nombre || ' ' || ur.apellido AS receptor, " +
                "       r.numero_remision " +
                "FROM movimientos_activos m " +
                "LEFT JOIN centros co ON co.id_centro = m.id_centro_origen " +
                "LEFT JOIN centros cd ON cd.id_centro = m.id_centro_destino " +
                "LEFT JOIN centros_costo cco ON cco.id_centro_costo = m.id_centro_costo_origen " +
                "LEFT JOIN centros_costo ccd ON ccd.id_centro_costo = m.id_centro_costo_destino " +
                "LEFT JOIN almacenes ao ON ao.id_almacen = m.id_almacen_origen " +
                "LEFT JOIN almacenes ad ON ad.id_almacen = m.id_almacen_destino " +
                "LEFT JOIN usuarios ug ON ug.id_usuario = m.id_usuario_registro " +
                "LEFT JOIN usuarios ue ON ue.id_usuario = m.id_usuario_emisor " +
                "LEFT JOIN usuarios ur ON ur.id_usuario = m.id_usuario_receptor " +
                "LEFT JOIN remisiones r ON r.id_remision = m.id_remision " +
                "WHERE m.id_equipo = ? ORDER BY m.fecha_movimiento DESC, m.id_movimiento DESC");
            ps.setInt(1, idEquipo);
            rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject m = new JSONObject();
                Timestamp fecha = rs.getTimestamp("fecha_movimiento");
                m.put("id", rs.getInt("id_movimiento"));
                m.put("fecha", fecha == null ? "" : fecha.toString().substring(0, 16));
                m.put("tipo", txt(rs.getString("tipo_movimiento")));
                m.put("numero", txt(rs.getString("numero_movimiento_unico")));
                m.put("estado_anterior", txt(rs.getString("estado_anterior")));
                m.put("estado_nuevo", txt(rs.getString("estado_nuevo")));
                m.put("observaciones", txt(rs.getString("observaciones")));
                m.put("justificacion", txt(rs.getString("justificacion_rechazo")));
                m.put("persona_nombre", txt(rs.getString("datos_receptor_nombre")));
                m.put("persona_apellido", txt(rs.getString("datos_receptor_apellido")));
                m.put("persona_ci", txt(rs.getString("datos_receptor_ci")));
                m.put("vehiculo_chapa", txt(rs.getString("datos_receptor_vehiculo_chapa")));
                m.put("vehiculo_marca", txt(rs.getString("datos_receptor_vehiculo_marca")));
                m.put("vehiculo_modelo", txt(rs.getString("datos_receptor_vehiculo_modelo")));
                m.put("centro_origen", txt(rs.getString("centro_origen")));
                m.put("centro_destino", txt(rs.getString("centro_destino")));
                // Ubicación dentro del centro: centro de costo (o almacén en movimientos anteriores)
                String subOrigen = nombreCC(rs.getString("cco_codigo"), rs.getString("cco_descripcion"));
                String subDestino = nombreCC(rs.getString("ccd_codigo"), rs.getString("ccd_descripcion"));
                m.put("sub_origen", subOrigen.isEmpty() ? txt(rs.getString("almacen_origen")) : subOrigen);
                m.put("sub_destino", subDestino.isEmpty() ? txt(rs.getString("almacen_destino")) : subDestino);
                m.put("registrado_por", txt(rs.getString("registrado_por")));
                m.put("emisor", txt(rs.getString("emisor")));
                m.put("receptor", txt(rs.getString("receptor")));
                m.put("remision", txt(rs.getString("numero_remision")));
                movs.put(m);
            }
            f.put("movimientos", movs);
            response.setContentType("application/json; charset=UTF-8");
            out.print(f.toString());
        }

        // ---------- REGISTRAR UN MOVIMIENTO ----------
        // Respuesta JSON: {ok:true, mensaje} o {ok:false, error}
        else if ("registrarMovimiento".equals(tipo)) {
            response.setContentType("application/json; charset=UTF-8");
            JSONObject resp = new JSONObject();
            int idUsuario = ((Integer) session.getAttribute("id_usuario")).intValue();
            int idEquipo = Integer.parseInt(request.getParameter("id_equipo"));
            String accion = request.getParameter("accion");

            conn.setAutoCommit(false);
            try {
                if (!Movimiento.ASIGNACION.equals(accion) && !Movimiento.DEVOLUCION.equals(accion)
                        && !Movimiento.REUBICACION.equals(accion)) {
                    throw new IllegalArgumentException("Movimiento no permitido");
                }
                // Bloquea el activo: dos usuarios no pueden moverlo a la vez
                ps = conn.prepareStatement("SELECT estado, id_centro_actual, id_centro_costo_actual FROM equipos WHERE id_equipo = ? FOR UPDATE");
                ps.setInt(1, idEquipo);
                rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("Activo no encontrado");
                }
                String estado = rs.getString("estado");
                Integer centroActual = entero(rs, "id_centro_actual");
                Integer ccActual = entero(rs, "id_centro_costo_actual");
                rs.close(); ps.close(); rs = null; ps = null;
                if (centroActual != null) {
                    permisos.exigirCentro(centroActual.intValue());
                }

                if (existe(conn, "SELECT 1 FROM solicitudes_activos_detalle WHERE id_equipo = ? AND estado_linea IN ('PENDIENTE', 'EN_CURSO')", idEquipo, 0)) {
                    throw new IllegalArgumentException("El activo está en una solicitud de salida o baja abierta; espere a que se resuelva en Aprobaciones");
                }
                Movimiento m = new Movimiento(accion, idEquipo, idUsuario);
                m.estadoAnterior = estado;
                m.observaciones = request.getParameter("observaciones");
                m.centroOrigen = centroActual;
                m.centroCostoOrigen = ccActual;
                String nuevoEstado = estado;
                Integer nuevoCC = ccActual;

                if (Movimiento.ASIGNACION.equals(accion)) {
                    exigirEstado(estado, "DISPONIBLE", "asignar");
                    String usuarioStr = request.getParameter("id_usuario_receptor");
                    boolean interno = usuarioStr != null && usuarioStr.trim().matches("[0-9]{1,9}");
                    if (interno) {
                        int receptor = Integer.parseInt(usuarioStr.trim());
                        if (!existe(conn, "SELECT 1 FROM usuarios WHERE id_usuario = ? AND activo = true", receptor, 0)) {
                            throw new IllegalArgumentException("El usuario responsable no existe o está inactivo");
                        }
                        m.usuarioReceptor = Integer.valueOf(receptor);
                    } else {
                        m.receptorNombre = request.getParameter("persona_nombre");
                        m.receptorApellido = request.getParameter("persona_apellido");
                        m.receptorCi = request.getParameter("persona_ci");
                        if (vacio(m.receptorNombre) || vacio(m.receptorApellido) || vacio(m.receptorCi)) {
                            throw new IllegalArgumentException("Elija un usuario o cargue nombre, apellido y CI del responsable");
                        }
                    }
                    m.usuarioEmisor = Integer.valueOf(idUsuario);
                    m.centroDestino = centroActual;
                    m.centroCostoDestino = ccActual;
                    nuevoEstado = "ASIGNADO";
                }
                else if (Movimiento.DEVOLUCION.equals(accion)) {
                    exigirEstado(estado, "ASIGNADO", "registrar la devolución de");
                    m.centroDestino = centroActual;
                    m.centroCostoDestino = ccActual;
                    m.usuarioReceptor = Integer.valueOf(idUsuario);
                    nuevoEstado = "DISPONIBLE";
                }
                else { // REUBICACION: otro centro de costo del mismo centro
                    if (!"DISPONIBLE".equals(estado) && !"ASIGNADO".equals(estado)) {
                        throw new IllegalArgumentException("No se puede cambiar el centro de costo: el estado actual es " + estado);
                    }
                    int cc = enteroParam(request, "id_centro_costo", "Seleccione el nuevo centro de costo");
                    if (ccActual != null && cc == ccActual.intValue()) {
                        throw new IllegalArgumentException("El activo ya está en ese centro de costo");
                    }
                    if (centroActual == null || !existe(conn, "SELECT 1 FROM centros_costo WHERE id_centro_costo = ? AND id_centro = ? AND activo = true", cc, centroActual.intValue())) {
                        throw new IllegalArgumentException("El centro de costo debe pertenecer al centro del activo y estar activo");
                    }
                    m.centroDestino = centroActual;
                    m.centroCostoDestino = Integer.valueOf(cc);
                    nuevoCC = Integer.valueOf(cc);
                }

                m.estadoNuevo = nuevoEstado;
                m.registrar(conn);

                ps = conn.prepareStatement("UPDATE equipos SET estado = ?, id_centro_costo_actual = ? WHERE id_equipo = ?");
                ps.setString(1, nuevoEstado);
                if (nuevoCC == null) { ps.setNull(2, Types.INTEGER); } else { ps.setInt(2, nuevoCC.intValue()); }
                ps.setInt(3, idEquipo);
                ps.executeUpdate();
                ps.close(); ps = null;

                conn.commit();
                resp.put("ok", true);
                resp.put("mensaje", "Movimiento registrado correctamente");
            } catch (IllegalArgumentException e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", Errores.mensaje(application, e));
            } catch (Exception e) {
                conn.rollback();
                resp.put("ok", false);
                resp.put("error", "Error al registrar el movimiento: " + Errores.mensaje(application, e));
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
            out.print(resp.toString());
        }

        // ---------- IMPRIMIR LISTADO (PDF, reportes/listadoActivos.jrxml) ----------
        // Respeta los filtros de la pantalla: centro, centro de costo, estado y pendientes
        /* (impresión en PDF: solo en la web) */
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


    /** Situación por una solicitud abierta: pendiente de aprobación, en reparación o en tránsito. */
    private static final String SQL_SITUACION =
        "(SELECT CASE WHEN d.estado_linea = 'PENDIENTE' THEN 'Solicitud ' || s.numero || ' pendiente de aprobación' " +
        "             WHEN s.tipo = 'REPARACION' THEN 'En reparación: ' || COALESCE(p.razon_social, '') " +
        "             ELSE 'En tránsito a ' || COALESCE(cd.nombre, '') || ' (' || s.numero || ')' END " +
        "   FROM solicitudes_activos_detalle d JOIN solicitudes_activos s ON s.id_solicitud = d.id_solicitud " +
        "   LEFT JOIN proveedores p ON p.id_proveedor = s.id_proveedor LEFT JOIN centros cd ON cd.id_centro = s.id_centro_destino " +
        "  WHERE d.id_equipo = e.id_equipo AND d.estado_linea IN ('PENDIENTE', 'EN_CURSO') LIMIT 1)";

    /** Responsable actual: la última ASIGNACION (usuario del sistema o persona externa). */
    private static final String SQL_RESPONSABLE =
        "(SELECT COALESCE(ur.nombre || ' ' || ur.apellido, " +
        "        NULLIF(TRIM(COALESCE(ma.datos_receptor_nombre, '') || ' ' || COALESCE(ma.datos_receptor_apellido, '')), '')) " +
        "   FROM movimientos_activos ma LEFT JOIN usuarios ur ON ur.id_usuario = ma.id_usuario_receptor " +
        "  WHERE ma.id_equipo = e.id_equipo AND ma.tipo_movimiento = 'ASIGNACION' " +
        "  ORDER BY ma.fecha_movimiento DESC, ma.id_movimiento DESC LIMIT 1)";

    private static final String[] ESTADOS_VALIDOS = {
        "SOLICITADO", "DISPONIBLE", "ASIGNADO", "EN_TRANSITO", "EN_REPARACION", "BAJA", "ANULADO"
    };

    private static boolean estadoValido(String estado) {
        if (estado == null) {
            return false;
        }
        for (int i = 0; i < ESTADOS_VALIDOS.length; i++) {
            if (ESTADOS_VALIDOS[i].equals(estado)) {
                return true;
            }
        }
        return false;
    }

    /** El número provisorio (PEND-...) de un activo aún no recibido no se muestra. */
    private static String numeroActivo(String serial) {
        return (serial == null || serial.startsWith("PEND-")) ? "" : serial;
    }

    private static String nombreCC(String codigo, String descripcion) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return "";
        }
        return (descripcion == null || descripcion.trim().isEmpty()) ? codigo : codigo + " - " + descripcion;
    }

    private static void exigirEstado(String actual, String requerido, String verbo) {
        if (!requerido.equals(actual)) {
            throw new IllegalArgumentException("No se puede " + verbo + " el activo: su estado actual es " + actual);
        }
    }

    private static int numero(String v) {
        return (v != null && v.trim().matches("[0-9]{1,9}")) ? Integer.parseInt(v.trim()) : 0;
    }

    private static int enteroParam(HttpServletRequest req, String nombre, String mensaje) {
        String v = req.getParameter(nombre);
        if (v == null || !v.trim().matches("[0-9]{1,9}")) {
            throw new IllegalArgumentException(mensaje);
        }
        return Integer.parseInt(v.trim());
    }

    /** Ejecuta un SELECT 1 con uno o dos parámetros enteros (el segundo solo si el SQL lo tiene). */
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

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        int v = rs.getInt(columna);
        return rs.wasNull() ? null : Integer.valueOf(v);
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** null -> "" (en JSON un null haría desaparecer la clave) */
    private static String txt(String s) {
        return s == null ? "" : s;
    }

}
