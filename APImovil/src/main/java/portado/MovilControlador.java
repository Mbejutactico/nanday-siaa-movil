package portado;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import seguridad.Permisos;

/**
 * Servicio de la app que no existe como tal en la web: arma en JSON lo que la web
 * muestra en combos y tablas HTML (sesión, centros, almacenes, insumos, stock,
 * OT con salidas pendientes, OC por recibir...) y el ABM de proveedores e insumos.
 * Los referenciales de administración (centros, CC, almacenes, unidades, roles, usuarios) están en
 * ReferencialesControlador.
 * Las consultas aplican los mismos filtros por centro que la web (Permisos.sqlCentros).
 */
public final class MovilControlador {

    private MovilControlador() {
    }

    public static Object atender(HttpServletRequest req, Connection conn, Permisos p, int idUsuario) throws SQLException {
        String tipo = req.getParameter("tipo");
        if ("sesion".equals(tipo)) {
            return sesion(conn, p, idUsuario);
        }
        if ("centros".equals(tipo)) {
            boolean todos = "true".equals(req.getParameter("todos"));
            return lista(conn, "SELECT id_centro AS id, nombre FROM centros WHERE activo = true AND "
                    + (todos ? "TRUE" : p.sqlCentros("id_centro")) + " ORDER BY nombre");
        }
        if ("almacenes".equals(tipo)) {
            return almacenes(conn, p, numero(req.getParameter("id_centro")), "true".equals(req.getParameter("destino")));
        }
        if ("centrosCosto".equals(tipo)) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id_centro_costo AS id, codigo || COALESCE(' - ' || NULLIF(TRIM(descripcion), ''), '') AS nombre "
                    + "FROM centros_costo WHERE id_centro = ? AND activo = true ORDER BY codigo");
            ps.setInt(1, numero(req.getParameter("id_centro")));
            return lista(ps);
        }
        if ("insumos".equals(tipo)) {
            return insumosActivos(conn, p);
        }
        if ("stock".equals(tipo)) {
            return stock(conn, p, req);
        }
        if ("otsConPendientes".equals(tipo)) {
            return otsConPendientes(conn, p);
        }
        if ("ocPendientes".equals(tipo)) {
            return ocPendientes(conn, p);
        }
        if ("proveedoresActivos".equals(tipo)) {
            return lista(conn, "SELECT id_proveedor AS id, razon_social AS nombre FROM proveedores WHERE activo = true ORDER BY razon_social");
        }
        if ("otAbiertas".equals(tipo)) {
            // Igual que activosMovControlador (entrega de activos a una OT de cualquier centro)
            return lista(conn,
                    "SELECT o.id_ot AS id, o.numero_ot || ' - ' || COALESCE(o.titulo, '') || ' (' || COALESCE(c.nombre, '') "
                    + "|| COALESCE(' / ' || cc.codigo, '') || ')' AS nombre "
                    + "FROM ot o LEFT JOIN centros c ON c.id_centro = o.id_centro LEFT JOIN centros_costo cc ON cc.id_centro_costo = o.id_centro_costo "
                    + "WHERE o.estado IN ('PENDIENTE', 'EN_PROCESO') ORDER BY o.fecha_solicitud, o.id_ot");
        }
        if ("usuarios".equals(tipo)) {
            return lista(conn, "SELECT id_usuario AS id, apellido || ', ' || nombre AS nombre FROM usuarios WHERE activo = true ORDER BY apellido, nombre");
        }
        if ("unidades".equals(tipo)) {
            return lista(conn, "SELECT id_unidad_medida AS id, nombre || COALESCE(' (' || abreviatura || ')', '') AS nombre "
                    + "FROM unidad_medida WHERE activo = true ORDER BY nombre");
        }

        // ---------------- Proveedores ----------------
        if ("proveedores".equals(tipo)) {
            return proveedores(conn, 0);
        }
        if ("proveedor".equals(tipo)) {
            JSONArray uno = proveedores(conn, requerido(req, "id"));
            if (uno.length() == 0) {
                throw new IllegalArgumentException("El proveedor no existe");
            }
            return uno.getJSONObject(0);
        }
        if ("guardarProveedor".equals(tipo)) {
            return guardarProveedor(conn, req);
        }
        if ("eliminarProveedor".equals(tipo)) {
            return eliminar(conn, "DELETE FROM proveedores WHERE id_proveedor = ?", requerido(req, "id"),
                    "El proveedor no existe", "Proveedor eliminado correctamente");
        }

        // ---------------- Insumos (catálogo) ----------------
        if ("insumosCatalogo".equals(tipo)) {
            return insumosCatalogo(conn, 0);
        }
        if ("insumo".equals(tipo)) {
            JSONArray uno = insumosCatalogo(conn, requerido(req, "id"));
            if (uno.length() == 0) {
                throw new IllegalArgumentException("El insumo no existe");
            }
            return uno.getJSONObject(0);
        }
        if ("guardarInsumo".equals(tipo)) {
            return guardarInsumo(conn, req);
        }
        if ("eliminarInsumo".equals(tipo)) {
            return eliminar(conn, "DELETE FROM insumos WHERE id_insumo = ?", requerido(req, "id"),
                    "El insumo no existe", "Insumo eliminado correctamente");
        }
        // ---------------- Referenciales de administración (REF_ADMIN) ----------------
        Object referencial = ReferencialesControlador.atender(tipo, req, conn, idUsuario);
        if (referencial != null) {
            return referencial;
        }
        throw new IllegalArgumentException("Operación no reconocida");
    }

    // =====================================================================
    // Sesión: usuario, rol, permisos y centros (la app arma el menú con esto)
    // =====================================================================
    private static JSONObject sesion(Connection conn, Permisos p, int idUsuario) throws SQLException {
        JSONObject s = new JSONObject();
        PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id_usuario, u.nombre, u.apellido, u.email, u.foto_perfil, u.centro_asignado, c.nombre AS centro, "
                + "r.codigo AS rol, r.nombre AS rol_nombre FROM usuarios u LEFT JOIN roles r ON r.id_rol = u.id_rol "
                + "LEFT JOIN centros c ON c.id_centro = u.centro_asignado WHERE u.id_usuario = ?");
        try {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new IllegalArgumentException("Usuario no encontrado");
            }
            s.put("id", rs.getInt("id_usuario"));
            s.put("nombre", txt(rs.getString("nombre")));
            s.put("apellido", txt(rs.getString("apellido")));
            s.put("email", txt(rs.getString("email")));
            s.put("foto", util.FotosMovil.fotoDe(idUsuario));   // foto propia de la app, no la de la web
            s.put("id_centro", rs.getInt("centro_asignado"));
            s.put("centro", txt(rs.getString("centro")));
            s.put("rol", txt(rs.getString("rol")));
            s.put("rol_nombre", txt(rs.getString("rol_nombre")));
            rs.close();
        } finally {
            ps.close();
        }
        s.put("permisos", new JSONObject(p.comoJson()));
        s.put("centros", lista(conn, "SELECT id_centro AS id, nombre FROM centros WHERE activo = true AND "
                + p.sqlCentros("id_centro") + " ORDER BY nombre"));
        return s;
    }

    // =====================================================================
    // Combos
    // =====================================================================
    private static JSONArray almacenes(Connection conn, Permisos p, int idCentro, boolean destino) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT a.id_almacen, a.nombre, a.id_centro, c.nombre AS centro FROM almacenes a LEFT JOIN centros c ON c.id_centro = a.id_centro "
                + "WHERE a.activo = true AND (? = 0 OR a.id_centro = ?) AND "
                // destino de una transferencia: un almacén de cualquier centro
                + (destino ? "TRUE" : p.sqlCentros("a.id_centro")) + " ORDER BY c.nombre, a.nombre");
        JSONArray lista = new JSONArray();
        try {
            ps.setInt(1, idCentro);
            ps.setInt(2, idCentro);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_almacen"));
                o.put("nombre", txt(rs.getString("nombre")));
                o.put("id_centro", rs.getInt("id_centro"));
                o.put("centro", txt(rs.getString("centro")));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    /** Insumos activos con el stock total en los almacenes que el usuario opera. */
    private static JSONArray insumosActivos(Connection conn, Permisos p) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT i.id_insumo, i.codigo, i.descripcion, u.abreviatura, COALESCE(i.precio_compra, 0) AS precio, "
                + "COALESCE((SELECT SUM(s.stock_actual) FROM stock_almacen s JOIN almacenes a ON a.id_almacen = s.id_almacen "
                + "          WHERE s.id_insumo = i.id_insumo AND a.activo = true AND " + p.sqlCentros("a.id_centro") + "), 0) AS stock "
                + "FROM insumos i LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida "
                + "WHERE i.activo = true ORDER BY i.descripcion");
        JSONArray lista = new JSONArray();
        try {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_insumo"));
                o.put("codigo", txt(rs.getString("codigo")));
                o.put("nombre", txt(rs.getString("descripcion")));
                o.put("unidad", txt(rs.getString("abreviatura")));
                o.put("precio", rs.getBigDecimal("precio").toPlainString());
                o.put("stock", rs.getLong("stock"));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    /** Stock por almacén con filtros: id_centro, id_almacen, situacion (SIN_STOCK, BAJO_MINIMO, NORMAL), q (texto). */
    private static JSONArray stock(Connection conn, Permisos p, HttpServletRequest req) throws SQLException {
        int idCentro = numero(req.getParameter("id_centro"));
        int idAlmacen = numero(req.getParameter("id_almacen"));
        String situacion = req.getParameter("situacion");
        if (!"SIN_STOCK".equals(situacion) && !"BAJO_MINIMO".equals(situacion) && !"NORMAL".equals(situacion)) {
            situacion = "";
        }
        String q = limpio(req.getParameter("q"));
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM (SELECT s.id_stock, s.id_insumo, s.id_almacen, s.stock_actual, s.stock_minimo, i.codigo, i.descripcion, "
                + "       COALESCE(i.precio_compra, 0) AS precio, u.abreviatura, a.nombre AS almacen, a.id_centro, c.nombre AS centro, "
                + "       CASE WHEN s.stock_actual <= 0 THEN 'SIN_STOCK' WHEN s.stock_actual <= s.stock_minimo THEN 'BAJO_MINIMO' ELSE 'NORMAL' END AS situacion "
                + "  FROM stock_almacen s JOIN insumos i ON i.id_insumo = s.id_insumo JOIN almacenes a ON a.id_almacen = s.id_almacen "
                + "  LEFT JOIN centros c ON c.id_centro = a.id_centro LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida) x "
                + "WHERE (? = 0 OR x.id_centro = ?) AND (? = 0 OR x.id_almacen = ?) AND (? = '' OR x.situacion = ?) "
                + "AND (? = '' OR x.codigo ILIKE '%' || ? || '%' OR x.descripcion ILIKE '%' || ? || '%') AND " + p.sqlCentros("x.id_centro") + " "
                + "ORDER BY CASE x.situacion WHEN 'SIN_STOCK' THEN 0 WHEN 'BAJO_MINIMO' THEN 1 ELSE 2 END, x.centro, x.almacen, x.descripcion LIMIT 500");
        JSONArray lista = new JSONArray();
        try {
            ps.setInt(1, idCentro);
            ps.setInt(2, idCentro);
            ps.setInt(3, idAlmacen);
            ps.setInt(4, idAlmacen);
            ps.setString(5, situacion);
            ps.setString(6, situacion);
            ps.setString(7, q);
            ps.setString(8, q);
            ps.setString(9, q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                int actual = rs.getInt("stock_actual");
                BigDecimal precio = rs.getBigDecimal("precio");
                o.put("id", rs.getInt("id_stock"));
                o.put("id_insumo", rs.getInt("id_insumo"));
                o.put("id_almacen", rs.getInt("id_almacen"));
                o.put("codigo", txt(rs.getString("codigo")));
                o.put("descripcion", txt(rs.getString("descripcion")));
                o.put("unidad", txt(rs.getString("abreviatura")));
                o.put("centro", txt(rs.getString("centro")));
                o.put("almacen", txt(rs.getString("almacen")));
                o.put("actual", actual);
                o.put("minimo", rs.getInt("stock_minimo"));
                o.put("situacion", rs.getString("situacion"));
                o.put("valor", precio.multiply(new BigDecimal(actual)).toPlainString());
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    /** OT con insumos suministrados sin salida (misma consulta que inventarioControlador). */
    private static JSONArray otsConPendientes(Connection conn, Permisos p) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT o.id_ot, o.numero_ot, o.titulo, c.nombre AS centro, COUNT(*) AS lineas FROM ot o "
                + "JOIN detalle_ot d ON d.id_ot = o.id_ot "
                + "LEFT JOIN remisiones r ON r.id_remision = d.id_remision AND r.anulada = false "
                + "LEFT JOIN centros c ON c.id_centro = o.id_centro "
                + "WHERE o.estado IN ('PENDIENTE', 'EN_PROCESO') AND r.id_remision IS NULL AND " + p.sqlCentros("o.id_centro") + " "
                + "GROUP BY o.id_ot, o.numero_ot, o.titulo, c.nombre, o.fecha_solicitud ORDER BY o.fecha_solicitud, o.id_ot");
        JSONArray lista = new JSONArray();
        try {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_ot"));
                o.put("numero", txt(rs.getString("numero_ot")));
                o.put("titulo", txt(rs.getString("titulo")));
                o.put("centro", txt(rs.getString("centro")));
                o.put("lineas", rs.getInt("lineas"));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    /** OC pendientes de recepción (SOLICITADO o PARCIAL) de los centros del usuario. */
    private static JSONArray ocPendientes(Connection conn, Permisos p) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT c.id_compra, c.numero_oc, c.fecha_compra, c.estado, c.id_centro_destino, ce.nombre AS centro, pr.razon_social, "
                + "       (SELECT COUNT(*) FROM detalle_compra d WHERE d.id_compra = c.id_compra AND d.cantidad > d.cantidad_recibida) AS lineas "
                + "FROM compras c LEFT JOIN centros ce ON ce.id_centro = c.id_centro_destino "
                + "LEFT JOIN proveedores pr ON pr.id_proveedor = c.id_proveedor "
                + "WHERE c.estado IN ('SOLICITADO', 'PARCIAL') AND c.numero_oc IS NOT NULL AND " + p.sqlCentros("c.id_centro_destino") + " "
                + "ORDER BY c.numero_oc");
        JSONArray lista = new JSONArray();
        try {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_compra"));
                o.put("numero", txt(rs.getString("numero_oc")));
                o.put("fecha", rs.getDate("fecha_compra") == null ? "" : rs.getDate("fecha_compra").toString());
                o.put("estado", txt(rs.getString("estado")));
                o.put("id_centro", rs.getInt("id_centro_destino"));
                o.put("centro", txt(rs.getString("centro")));
                o.put("proveedor", txt(rs.getString("razon_social")));
                o.put("lineas", rs.getInt("lineas"));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    // =====================================================================
    // Proveedores
    // =====================================================================
    private static JSONArray proveedores(Connection conn, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT id_proveedor, razon_social, ruc, direccion, telefono, email, activo FROM proveedores "
                + "WHERE (? = 0 OR id_proveedor = ?) ORDER BY razon_social");
        JSONArray lista = new JSONArray();
        try {
            ps.setInt(1, id);
            ps.setInt(2, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_proveedor"));
                o.put("razon_social", txt(rs.getString("razon_social")));
                o.put("ruc", txt(rs.getString("ruc")));
                o.put("direccion", txt(rs.getString("direccion")));
                o.put("telefono", txt(rs.getString("telefono")));
                o.put("email", txt(rs.getString("email")));
                o.put("activo", rs.getBoolean("activo"));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    private static JSONObject guardarProveedor(Connection conn, HttpServletRequest req) throws SQLException {
        int id = numero(req.getParameter("id"));
        String razon = texto(req, "razon_social", "La razón social", 200, true);
        String ruc = texto(req, "ruc", "El RUC", 20, true);
        String direccion = texto(req, "direccion", "La dirección", 300, false);
        String telefono = texto(req, "telefono", "El teléfono", 50, false);
        String email = texto(req, "email", "El email", 150, false);
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("El email no tiene un formato válido");
        }
        boolean activo = !"false".equals(req.getParameter("activo"));
        PreparedStatement ps = conn.prepareStatement(id == 0
                ? "INSERT INTO proveedores (razon_social, ruc, direccion, telefono, email, activo) VALUES (?, ?, ?, ?, ?, ?)"
                : "UPDATE proveedores SET razon_social = ?, ruc = ?, direccion = ?, telefono = ?, email = ?, activo = ? WHERE id_proveedor = ?");
        try {
            ps.setString(1, razon);
            ps.setString(2, ruc);
            nulo(ps, 3, direccion);
            nulo(ps, 4, telefono);
            nulo(ps, 5, email);
            ps.setBoolean(6, activo);
            if (id != 0) {
                ps.setInt(7, id);
            }
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("El proveedor no existe");
            }
        } finally {
            ps.close();
        }
        return ok(id == 0 ? "Proveedor guardado correctamente" : "Proveedor modificado correctamente");
    }

    // =====================================================================
    // Insumos (catálogo)
    // =====================================================================
    private static JSONArray insumosCatalogo(Connection conn, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT i.id_insumo, i.codigo, i.descripcion, COALESCE(i.precio_compra, 0) AS precio, i.id_unidad_medida, i.activo, "
                + "u.nombre || COALESCE(' (' || u.abreviatura || ')', '') AS unidad "
                + "FROM insumos i LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida "
                + "WHERE (? = 0 OR i.id_insumo = ?) ORDER BY i.descripcion");
        JSONArray lista = new JSONArray();
        try {
            ps.setInt(1, id);
            ps.setInt(2, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id_insumo"));
                o.put("codigo", txt(rs.getString("codigo")));
                o.put("descripcion", txt(rs.getString("descripcion")));
                o.put("precio_compra", rs.getBigDecimal("precio").toPlainString());
                o.put("id_unidad", rs.getInt("id_unidad_medida"));
                o.put("unidad", txt(rs.getString("unidad")));
                o.put("activo", rs.getBoolean("activo"));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    private static JSONObject guardarInsumo(Connection conn, HttpServletRequest req) throws SQLException {
        int id = numero(req.getParameter("id"));
        String codigo = texto(req, "codigo", "El código", 50, true);
        String descripcion = texto(req, "descripcion", "La descripción", 300, true);
        String precio = limpio(req.getParameter("precio_compra")).replace(",", ".");
        if (precio.isEmpty()) {
            precio = "0";
        }
        if (!precio.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("El precio debe ser un número mayor o igual a cero (hasta 2 decimales)");
        }
        int idUnidad = numero(req.getParameter("id_unidad"));
        boolean activo = !"false".equals(req.getParameter("activo"));
        PreparedStatement ps = conn.prepareStatement(id == 0
                ? "INSERT INTO insumos (codigo, descripcion, precio_compra, id_unidad_medida, activo) VALUES (?, ?, ?, ?, ?)"
                : "UPDATE insumos SET codigo = ?, descripcion = ?, precio_compra = ?, id_unidad_medida = ?, activo = ? WHERE id_insumo = ?");
        try {
            ps.setString(1, codigo);
            ps.setString(2, descripcion);
            ps.setBigDecimal(3, new BigDecimal(precio));
            if (idUnidad > 0) {
                ps.setInt(4, idUnidad);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setBoolean(5, activo);
            if (id != 0) {
                ps.setInt(6, id);
            }
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("El insumo no existe");
            }
        } finally {
            ps.close();
        }
        return ok(id == 0 ? "Insumo guardado correctamente" : "Insumo modificado correctamente");
    }

    // =====================================================================
    // Utilidades
    // =====================================================================
    private static JSONObject eliminar(Connection conn, String sql, int id, String noExiste, String mensaje) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException(noExiste);
            }
        } finally {
            ps.close();
        }
        return ok(mensaje);
    }

    /** SELECT con columnas id y nombre -> [{id, nombre}] */
    private static JSONArray lista(Connection conn, String sql) throws SQLException {
        return lista(conn.prepareStatement(sql));
    }

    private static JSONArray lista(PreparedStatement ps) throws SQLException {
        JSONArray lista = new JSONArray();
        try {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getInt("id"));
                o.put("nombre", txt(rs.getString("nombre")));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    private static JSONObject ok(String mensaje) {
        return new JSONObject().put("ok", true).put("mensaje", mensaje);
    }

    private static JSONObject error(String mensaje) {
        return new JSONObject().put("ok", false).put("error", mensaje);
    }

    private static String texto(HttpServletRequest req, String nombre, String etiqueta, int maximo, boolean obligatorio) {
        String v = limpio(req.getParameter(nombre));
        if (obligatorio && v.isEmpty()) {
            throw new IllegalArgumentException(etiqueta + " es un dato obligatorio");
        }
        if (v.length() > maximo) {
            throw new IllegalArgumentException(etiqueta + " admite hasta " + maximo + " caracteres");
        }
        return v;
    }

    private static int requerido(HttpServletRequest req, String nombre) {
        int v = numero(req.getParameter(nombre));
        if (v <= 0) {
            throw new IllegalArgumentException("Falta el dato " + nombre);
        }
        return v;
    }

    private static void nulo(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null || v.isEmpty()) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, v);
        }
    }

    static int numero(String v) {
        return (v != null && v.trim().matches("[0-9]{1,9}")) ? Integer.parseInt(v.trim()) : 0;
    }

    private static String limpio(String v) {
        return v == null ? "" : v.trim();
    }

    private static String txt(String s) {
        return s == null ? "" : s;
    }
}
