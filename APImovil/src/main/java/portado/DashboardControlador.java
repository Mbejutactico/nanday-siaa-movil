package portado;

import java.io.PrintWriter;
import java.sql.*;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.*;
import seguridad.Permisos;
import seguridad.SesionDatos;
import util.Errores;
import util.Html;

/**
 * Copia de la web: Controlador/dashboardcontrolador.jsp
 * La lógica (validaciones, transacciones, stock y Kardex) es la misma que usa el sistema web.
 * Generado por portar.py; solo se quitaron las operaciones de impresión en PDF.
 */
@SuppressWarnings({"unused", "rawtypes"})
public final class DashboardControlador {

    private DashboardControlador() {
    }

    public static void atender(HttpServletRequest request, HttpServletResponse response, PrintWriter out, Connection conn,
                               Permisos permisos, SesionDatos session, ServletContext application) throws Exception {

    // Todas las respuestas son JSON. Cada panel se filtra por los centros del usuario
    // y solo devuelve datos si su rol puede ver ese módulo.
    String tipo = request.getParameter("tipo");
    PreparedStatement ps = null;
    ResultSet rs = null;
    JSONObject resp = new JSONObject();
    try {
        // ---------- Contadores de los accesos rápidos ----------
        if ("contar".equals(tipo)) {
            resp.put("TOTAL_PROVEEDORES", contar(conn, "SELECT COUNT(*) FROM proveedores WHERE activo = true"));
            resp.put("TOTAL_ALMACENES", contar(conn, "SELECT COUNT(*) FROM almacenes WHERE activo = true"));
            resp.put("TOTAL_CENTROS", contar(conn, "SELECT COUNT(*) FROM centros WHERE activo = true"));
            resp.put("TOTAL_USUARIOS", contar(conn, "SELECT COUNT(*) FROM usuarios WHERE activo = true"));
            resp.put("TOTAL_INSUMOS", contar(conn, "SELECT COUNT(*) FROM insumos WHERE activo = true"));
            resp.put("TOTAL_ROLES", contar(conn, "SELECT COUNT(*) FROM roles WHERE activo = true"));
        }

        // ---------- Panel de órdenes de trabajo ----------
        // Contadores + las OT abiertas más antiguas (las que más urge atender).
        else if ("resumenOT".equals(tipo)) {
            if (!permisos.tieneAlguno(Permisos.OT_GESTION, Permisos.OT_SUMINISTRAR, Permisos.OT_FINALIZAR)) {
                resp.put("sin_permiso", true);
            } else {
                // Visibles: las de sus centros (destino), las que pidió y las que tiene a cargo
                int idUsuario = ((Integer) session.getAttribute("id_usuario")).intValue();
                String centros = "(" + permisos.sqlCentros("o.id_centro") + " OR o.id_usuario_solic = " + idUsuario
                        + " OR o.id_usuario_responsable = " + idUsuario + ")";
                resp.put("pendientes", contar(conn, "SELECT COUNT(*) FROM ot o WHERE o.estado = 'PENDIENTE' AND " + centros));
                resp.put("en_proceso", contar(conn, "SELECT COUNT(*) FROM ot o WHERE o.estado = 'EN_PROCESO' AND " + centros));
                resp.put("finalizadas_mes", contar(conn,
                        "SELECT COUNT(*) FROM ot o WHERE o.estado = 'FINALIZADA' "
                        + "AND o.fecha_finalizacion >= date_trunc('month', CURRENT_DATE) AND " + centros));
                resp.put("a_mi_cargo", contar(conn,
                        "SELECT COUNT(*) FROM ot o WHERE o.estado IN ('PENDIENTE', 'EN_PROCESO') AND o.id_usuario_responsable = " + idUsuario));
                resp.put("demoradas", contar(conn,
                        "SELECT COUNT(*) FROM ot o WHERE o.estado IN ('PENDIENTE', 'EN_PROCESO') "
                        + "AND o.fecha_creacion < CURRENT_TIMESTAMP - INTERVAL '15 days' AND " + centros));

                ps = conn.prepareStatement(
                    "SELECT o.id_ot, o.numero_ot, o.titulo, o.estado, c.nombre AS centro, " +
                    "       cc.codigo AS cc_codigo, ur.nombre || ' ' || ur.apellido AS responsable, " +
                    "       (CURRENT_DATE - CAST(o.fecha_creacion AS date)) AS dias, " +
                    "       (SELECT COUNT(*) FROM detalle_ot d WHERE d.id_ot = o.id_ot) AS items " +
                    "FROM ot o LEFT JOIN centros c ON c.id_centro = o.id_centro " +
                    "LEFT JOIN centros_costo cc ON cc.id_centro_costo = o.id_centro_costo " +
                    "LEFT JOIN usuarios ur ON ur.id_usuario = o.id_usuario_responsable " +
                    "WHERE o.estado IN ('PENDIENTE', 'EN_PROCESO') AND " + centros + " " +
                    "ORDER BY o.fecha_creacion, o.id_ot LIMIT 5");
                rs = ps.executeQuery();
                JSONArray lista = new JSONArray();
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("id", rs.getInt("id_ot"));
                    o.put("numero", rs.getString("numero_ot"));
                    o.put("titulo", rs.getString("titulo") == null ? "" : rs.getString("titulo"));
                    o.put("estado", rs.getString("estado"));
                    o.put("centro", rs.getString("centro") == null ? "" : rs.getString("centro"));
                    o.put("centro_costo", rs.getString("cc_codigo") == null ? "" : rs.getString("cc_codigo"));
                    o.put("dias", rs.getInt("dias"));
                    o.put("items", rs.getInt("items"));
                    o.put("responsable", rs.getString("responsable") == null ? "" : rs.getString("responsable"));
                    lista.put(o);
                }
                resp.put("abiertas", lista);
            }
        }

        // ---------- Últimas órdenes de compra ----------
        else if ("ultimasCompras".equals(tipo)) {
            if (!permisos.tiene(Permisos.OC_VER)) {
                resp.put("sin_permiso", true);
            } else {
                ps = conn.prepareStatement(
                    "SELECT c.id_compra, c.numero_oc, c.fecha_compra, p.razon_social, c.estado " +
                    "FROM compras c JOIN proveedores p ON c.id_proveedor = p.id_proveedor " +
                    "WHERE c.estado IN ('SOLICITADO', 'RECIBIDO', 'PARCIAL') AND " + permisos.sqlCentros("c.id_centro_destino") + " " +
                    "ORDER BY c.id_compra DESC LIMIT 5");
                rs = ps.executeQuery();
                JSONArray lista = new JSONArray();
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("id", rs.getInt("id_compra"));
                    o.put("numero", rs.getString("numero_oc") == null ? "" : rs.getString("numero_oc"));
                    o.put("proveedor", rs.getString("razon_social") == null ? "" : rs.getString("razon_social"));
                    o.put("fecha", rs.getDate("fecha_compra") == null ? "" : new java.text.SimpleDateFormat("dd/MM/yyyy").format(rs.getDate("fecha_compra")));
                    o.put("estado", rs.getString("estado"));
                    lista.put(o);
                }
                resp.put("compras", lista);
            }
        }

        // ---------- Gráficos del tablero (iguales a los de la web) ----------
        // Cada gráfico solo se envía si el rol puede ver esos datos.
        else if ("graficos".equals(tipo)) {
            int idUsuario = ((Integer) session.getAttribute("id_usuario")).intValue();
            if (permisos.tieneAlguno(Permisos.OT_GESTION, Permisos.OT_SUMINISTRAR, Permisos.OT_FINALIZAR)) {
                String visibles = "(" + permisos.sqlCentros("o.id_centro") + " OR o.id_usuario_solic = " + idUsuario
                        + " OR o.id_usuario_responsable = " + idUsuario + ")";
                resp.put("ot_estado", serie(conn, "SELECT e.estado, COUNT(o.id_ot), 0 "
                        + "FROM (VALUES (1, 'PENDIENTE'), (2, 'EN_PROCESO'), (3, 'FINALIZADA'), (4, 'ANULADA')) AS e(orden, estado) "
                        + "LEFT JOIN ot o ON o.estado = e.estado AND o.fecha_creacion >= CURRENT_DATE - INTERVAL '12 months' AND " + visibles + " "
                        + "GROUP BY e.orden, e.estado ORDER BY e.orden"));
            }
            if (permisos.tiene(Permisos.OC_VER)) {
                resp.put("oc_mes", serie(conn, "SELECT to_char(m.mes, 'MM/YYYY'), COUNT(x.id_compra), COALESCE(SUM(x.monto), 0) "
                        + "FROM generate_series(date_trunc('month', CURRENT_DATE) - INTERVAL '5 months', date_trunc('month', CURRENT_DATE), INTERVAL '1 month') AS m(mes) "
                        + "LEFT JOIN (SELECT c.id_compra, date_trunc('month', c.fecha_compra) AS mes, "
                        + "                  (SELECT COALESCE(SUM(d.cantidad * d.precio_unitario), 0) FROM detalle_compra d WHERE d.id_compra = c.id_compra) AS monto "
                        + "           FROM compras c WHERE c.estado IN ('SOLICITADO', 'PARCIAL', 'RECIBIDO') AND " + permisos.sqlCentros("c.id_centro_destino") + ") x "
                        + "ON x.mes = m.mes GROUP BY m.mes ORDER BY m.mes"));
            }
            if (permisos.tieneAlguno(Permisos.INVENTARIO, Permisos.INFORMES)) {
                resp.put("consumo_cc", serie(conn, "SELECT cc.codigo, SUM(-m.cantidad), COALESCE(SUM(-m.cantidad * COALESCE(m.costo_unitario, 0)), 0) AS valor "
                        + "FROM movimientos_stock m JOIN centros_costo cc ON cc.id_centro_costo = m.id_centro_costo "
                        + "WHERE m.tipo = 'SALIDA_OT' AND m.fecha >= CURRENT_DATE - INTERVAL '90 days' AND " + permisos.sqlCentros("cc.id_centro") + " "
                        + "GROUP BY cc.codigo ORDER BY valor DESC, cc.codigo LIMIT 8"));
            }
        }
        else {
            resp.put("error", "Operación no reconocida");
        }
    } catch (Exception e) {
        resp = new JSONObject();
        resp.put("error", Errores.mensaje(application, e));
    } finally {
        if (rs != null) try { rs.close(); } catch (Exception e) {}
        if (ps != null) try { ps.close(); } catch (Exception e) {}
    }
    out.print(resp.toString());

    }


    /** Serie de un gráfico: [{etiqueta, cantidad, valor}] (la consulta devuelve etiqueta, cantidad y valor). */
    private static JSONArray serie(Connection conn, String sql) throws SQLException {
        PreparedStatement p = conn.prepareStatement(sql);
        try {
            ResultSet r = p.executeQuery();
            JSONArray a = new JSONArray();
            while (r.next()) {
                JSONObject x = new JSONObject();
                x.put("etiqueta", r.getString(1) == null ? "" : r.getString(1));
                x.put("cantidad", r.getLong(2));
                x.put("valor", r.getBigDecimal(3) == null ? "0" : r.getBigDecimal(3).toPlainString());
                a.put(x);
            }
            r.close();
            return a;
        } finally {
            p.close();
        }
    }

    private static int contar(Connection conn, String sql) throws SQLException {
        PreparedStatement p = conn.prepareStatement(sql);
        try {
            ResultSet r = p.executeQuery();
            r.next();
            int n = r.getInt(1);
            r.close();
            return n;
        } finally {
            p.close();
        }
    }

}
