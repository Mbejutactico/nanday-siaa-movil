package portado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import seguridad.HistorialCentros;
import seguridad.Password;

/**
 * Referenciales de administración para la app (permiso REF_ADMIN, como en la web):
 * centros, centros de costo, almacenes, unidades de medida, roles y usuarios (con sus centros adicionales).
 * Cada alta, modificación o baja de un acceso a un centro queda en usuario_centros_historial,
 * en la misma transacción que el cambio (como en la web).
 *
 * Copia de las reglas de la web (proyecto Archivador, capa Servicio):
 *   CentroServicio, CentroCostoServicio, AlmacenServicio, UnidadMedidaServicio, RolServicio, UsuarioServicio.
 * Mismas validaciones y mismos mensajes. Un registro en uso (clave foránea) o un dato repetido lo traduce
 * util.Errores con el mismo texto que la web.
 *
 * Todas las operaciones van por POST /c/movil (tipo=...). Respuesta: lista JSON, o {ok, mensaje} / {ok:false, error}.
 */
public final class ReferencialesControlador {

    /** Tipos de rol (roles.codigo): igual que modelo/TipoRol.java de la web. */
    private static final Map<String, String[]> TIPOS_ROL = new LinkedHashMap<String, String[]>();

    static {
        TIPOS_ROL.put("ADMINISTRADOR", new String[]{"Administrador", "Acceso total a todas las pantallas y a todos los centros."});
        TIPOS_ROL.put("GERENTE", new String[]{"Gerente", "Compras (ver y recibir OC), órdenes de trabajo completas, inventario, activos con aprobaciones e informes. Solo sus centros."});
        TIPOS_ROL.put("AUXILIAR", new String[]{"Auxiliar", "Igual que Gerente, pero sin finalizar OT ni aprobar o recibir activos. Solo sus centros."});
        TIPOS_ROL.put("ENCARGADO_COMPRAS", new String[]{"Encargado de compras", "Proveedores, insumos, solicitudes y órdenes de compra, OT (sin suministrar ni finalizar) e informes. Todos los centros."});
        TIPOS_ROL.put("CONSULTOR", new String[]{"Consultor", "Solo informes. Todos los centros."});
    }

    /** Operaciones que atiende esta clase (para registrar el permiso en ReglasAcceso). */
    public static final String OPERACIONES =
            "refCentros,guardarCentro,eliminarCentro,"
            + "refCentrosCosto,guardarCentroCosto,eliminarCentroCosto,"
            + "refAlmacenes,guardarAlmacen,eliminarAlmacen,"
            + "refUnidades,guardarUnidad,eliminarUnidad,"
            + "refRoles,tiposRol,rolesActivos,guardarRol,eliminarRol,"
            + "refUsuarios,guardarUsuario,eliminarUsuario,centrosUsuario,agregarCentroUsuario,quitarCentroUsuario";

    private ReferencialesControlador() {
    }

    /** Atiende la operación o devuelve null si no es de esta clase. */
    public static Object atender(String tipo, HttpServletRequest req, Connection conn, int idUsuario) throws SQLException {
        if (tipo == null) {
            return null;
        }
        switch (tipo) {
            // ---------------- Centros ----------------
            case "refCentros":
                return consulta(conn, "SELECT id_centro AS id, codigo, nombre, direccion, activo FROM centros ORDER BY id_centro");
            case "guardarCentro":
                return guardarCentro(conn, req);
            case "eliminarCentro":
                return eliminar(conn, "DELETE FROM centros WHERE id_centro = ?", req,
                        "El centro ya no existe. Actualice la lista.", "Centro eliminado con éxito");

            // ---------------- Centros de costo ----------------
            case "refCentrosCosto":
                return consulta(conn, "SELECT cc.id_centro_costo AS id, cc.codigo, cc.descripcion, cc.id_centro, "
                        + "COALESCE(c.nombre, '') AS centro, cc.activo "
                        + "FROM centros_costo cc LEFT JOIN centros c ON cc.id_centro = c.id_centro ORDER BY cc.id_centro_costo");
            case "guardarCentroCosto":
                return guardarCentroCosto(conn, req);
            case "eliminarCentroCosto":
                return eliminar(conn, "DELETE FROM centros_costo WHERE id_centro_costo = ?", req,
                        "El centro de costo ya no existe. Actualice la lista.", "Centro de costo eliminado con éxito");

            // ---------------- Almacenes ----------------
            case "refAlmacenes":
                return consulta(conn, "SELECT a.id_almacen AS id, a.nombre, a.descripcion, a.ubicacion, a.id_centro, "
                        + "COALESCE(c.nombre, '') AS centro, a.activo "
                        + "FROM almacenes a LEFT JOIN centros c ON a.id_centro = c.id_centro ORDER BY a.id_almacen");
            case "guardarAlmacen":
                return guardarAlmacen(conn, req);
            case "eliminarAlmacen":
                return eliminar(conn, "DELETE FROM almacenes WHERE id_almacen = ?", req,
                        "El almacén ya no existe. Actualice la lista.", "Almacén eliminado con éxito");

            // ---------------- Unidades de medida ----------------
            case "refUnidades":
                return consulta(conn, "SELECT u.id_unidad_medida AS id, u.nombre, u.abreviatura, u.activo, "
                        + "(SELECT COUNT(*) FROM insumos i WHERE i.id_unidad_medida = u.id_unidad_medida) AS insumos "
                        + "FROM unidad_medida u ORDER BY u.id_unidad_medida");
            case "guardarUnidad":
                return guardarUnidad(conn, req);
            case "eliminarUnidad":
                return eliminarUnidad(conn, req);

            // ---------------- Roles ----------------
            case "refRoles":
                return roles(conn);
            case "tiposRol":
                return tiposRol();
            case "rolesActivos":
                return consulta(conn, "SELECT id_rol AS id, nombre, codigo FROM roles WHERE activo = true ORDER BY nombre");
            case "guardarRol":
                return guardarRol(conn, req);
            case "eliminarRol":
                return eliminarRol(conn, req);

            // ---------------- Usuarios ----------------
            case "refUsuarios":
                return consulta(conn, "SELECT u.id_usuario AS id, u.nombre, u.apellido, u.email, u.id_rol, "
                        + "COALESCE(r.nombre, '') AS rol, COALESCE(r.codigo, '') AS rol_codigo, u.activo, u.centro_asignado, "
                        + "COALESCE(c.nombre, '') AS centro, "
                        + "(SELECT COUNT(*) FROM usuario_centros uc WHERE uc.id_usuario = u.id_usuario) AS centros_adicionales "
                        + "FROM usuarios u LEFT JOIN roles r ON u.id_rol = r.id_rol "
                        + "LEFT JOIN centros c ON u.centro_asignado = c.id_centro ORDER BY u.id_usuario");
            case "guardarUsuario":
                return guardarUsuario(conn, req, idUsuario);
            case "eliminarUsuario":
                return eliminarUsuario(conn, req, idUsuario);
            case "centrosUsuario":
                return centrosUsuario(conn, requerido(req, "id"));
            case "agregarCentroUsuario":
                return agregarCentroUsuario(conn, req, idUsuario);
            case "quitarCentroUsuario":
                return quitarCentroUsuario(conn, req, idUsuario);
            default:
                return null;
        }
    }

    // =====================================================================
    // Centros (CentroServicio)
    // =====================================================================
    private static JSONObject guardarCentro(Connection conn, HttpServletRequest req) throws SQLException {
        int id = numero(req.getParameter("id"));
        String codigo = obligatorio(req.getParameter("codigo"), "Ingrese el código del centro.");
        String nombre = obligatorio(req.getParameter("nombre"), "Ingrese el nombre del centro.");
        String direccion = limpio(req.getParameter("direccion"));
        boolean activo = !"false".equals(req.getParameter("activo"));
        int filas = ejecutar(conn, id == 0
                        ? "INSERT INTO centros (codigo, nombre, direccion, activo) VALUES (?, ?, ?, ?)"
                        : "UPDATE centros SET codigo = ?, nombre = ?, direccion = ?, activo = ? WHERE id_centro = ?",
                id, codigo, nombre, nulo(direccion), activo);
        existe(filas, "El centro ya no existe. Actualice la lista.");
        return ok(id == 0 ? "Centro guardado con éxito" : "Centro modificado con éxito");
    }

    // =====================================================================
    // Centros de costo (CentroCostoServicio)
    // =====================================================================
    private static JSONObject guardarCentroCosto(Connection conn, HttpServletRequest req) throws SQLException {
        int id = numero(req.getParameter("id"));
        String codigo = obligatorio(req.getParameter("codigo"), "Ingrese el código del centro de costo.");
        int idCentro = numero(req.getParameter("id_centro"));
        if (idCentro <= 0) {
            throw new IllegalArgumentException("Seleccione el centro al que pertenece el centro de costo.");
        }
        String descripcion = limpio(req.getParameter("descripcion"));
        boolean activo = !"false".equals(req.getParameter("activo"));
        int filas = ejecutar(conn, id == 0
                        ? "INSERT INTO centros_costo (codigo, descripcion, id_centro, activo) VALUES (?, ?, ?, ?)"
                        : "UPDATE centros_costo SET codigo = ?, descripcion = ?, id_centro = ?, activo = ? WHERE id_centro_costo = ?",
                id, codigo, nulo(descripcion), Integer.valueOf(idCentro), activo);
        existe(filas, "El centro de costo ya no existe. Actualice la lista.");
        return ok(id == 0 ? "Centro de costo guardado con éxito" : "Centro de costo modificado con éxito");
    }

    // =====================================================================
    // Almacenes (AlmacenServicio)
    // =====================================================================
    private static JSONObject guardarAlmacen(Connection conn, HttpServletRequest req) throws SQLException {
        int id = numero(req.getParameter("id"));
        String nombre = obligatorio(req.getParameter("nombre"), "Ingrese el nombre del almacén.");
        int idCentro = numero(req.getParameter("id_centro"));
        if (idCentro <= 0) {
            throw new IllegalArgumentException("Seleccione el centro al que pertenece el almacén.");
        }
        String descripcion = limpio(req.getParameter("descripcion"));
        String ubicacion = limpio(req.getParameter("ubicacion"));
        boolean activo = !"false".equals(req.getParameter("activo"));
        int filas = ejecutar(conn, id == 0
                        ? "INSERT INTO almacenes (nombre, descripcion, ubicacion, id_centro, activo) VALUES (?, ?, ?, ?, ?)"
                        : "UPDATE almacenes SET nombre = ?, descripcion = ?, ubicacion = ?, id_centro = ?, activo = ? WHERE id_almacen = ?",
                id, nombre, nulo(descripcion), nulo(ubicacion), Integer.valueOf(idCentro), activo);
        existe(filas, "El almacén ya no existe. Actualice la lista.");
        return ok(id == 0 ? "Almacén guardado con éxito" : "Almacén modificado con éxito");
    }

    // =====================================================================
    // Unidades de medida (UnidadMedidaServicio)
    // =====================================================================
    private static JSONObject guardarUnidad(Connection conn, HttpServletRequest req) throws SQLException {
        int id = numero(req.getParameter("id"));
        String nombre = obligatorio(req.getParameter("nombre"), "Ingrese el nombre de la unidad de medida.");
        String abreviatura = limpio(req.getParameter("abreviatura"));
        boolean activo = !"false".equals(req.getParameter("activo"));
        int filas = ejecutar(conn, id == 0
                        ? "INSERT INTO unidad_medida (nombre, abreviatura, activo) VALUES (?, ?, ?)"
                        : "UPDATE unidad_medida SET nombre = ?, abreviatura = ?, activo = ? WHERE id_unidad_medida = ?",
                id, nombre, nulo(abreviatura), activo);
        existe(filas, "La unidad de medida ya no existe. Actualice la lista.");
        return ok(id == 0 ? "Unidad guardada con éxito" : "Unidad modificada con éxito");
    }

    private static JSONObject eliminarUnidad(Connection conn, HttpServletRequest req) throws SQLException {
        int id = requerido(req, "id");
        if (entero(conn, "SELECT COUNT(*) FROM insumos WHERE id_unidad_medida = ?", id) > 0) {
            throw new IllegalArgumentException("No se puede eliminar: la unidad está siendo utilizada por uno o más insumos.");
        }
        return eliminar(conn, "DELETE FROM unidad_medida WHERE id_unidad_medida = ?", id,
                "La unidad de medida ya no existe. Actualice la lista.", "Unidad eliminada con éxito");
    }

    // =====================================================================
    // Roles (RolServicio)
    // =====================================================================
    private static JSONArray roles(Connection conn) throws SQLException {
        JSONArray lista = consulta(conn, "SELECT r.id_rol AS id, r.nombre, r.descripcion, r.activo, r.codigo, "
                + "(SELECT COUNT(*) FROM usuarios u WHERE u.id_rol = r.id_rol) AS usuarios FROM roles r ORDER BY r.id_rol");
        for (int i = 0; i < lista.length(); i++) {
            JSONObject o = lista.getJSONObject(i);
            String[] t = TIPOS_ROL.get(o.optString("codigo"));
            o.put("tipo", t == null ? "(sin tipo)" : t[0]);
        }
        return lista;
    }

    private static JSONArray tiposRol() {
        JSONArray lista = new JSONArray();
        for (Map.Entry<String, String[]> e : TIPOS_ROL.entrySet()) {
            lista.put(new JSONObject().put("codigo", e.getKey()).put("nombre", e.getValue()[0]).put("descripcion", e.getValue()[1]));
        }
        return lista;
    }

    /** Guarda o modifica el rol. Nunca deja el sistema sin un administrador activo. */
    private static JSONObject guardarRol(Connection conn, HttpServletRequest req) throws SQLException {
        int id = numero(req.getParameter("id"));
        boolean nuevo = id == 0;
        String nombre = limpio(req.getParameter("nombre"));
        String descripcion = limpio(req.getParameter("descripcion"));
        String codigo = limpio(req.getParameter("codigo"));
        boolean activo = !"false".equals(req.getParameter("activo"));
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre del rol es obligatorio.");
        }
        if (nombre.length() > 50) {
            throw new IllegalArgumentException("El nombre del rol puede tener hasta 50 caracteres.");
        }
        if (!TIPOS_ROL.containsKey(codigo)) {
            throw new IllegalArgumentException("Seleccione el tipo de permisos del rol.");
        }
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int filas = ejecutar(conn, nuevo
                            ? "INSERT INTO roles (nombre, descripcion, activo, codigo) VALUES (?, ?, ?, ?)"
                            : "UPDATE roles SET nombre = ?, descripcion = ?, activo = ?, codigo = ? WHERE id_rol = ?",
                    id, nombre, descripcion, activo, codigo);
            if (filas == 0) {
                throw new IllegalArgumentException("El rol ya no existe. Actualice la lista.");
            }
            // Nunca dejar el sistema sin un administrador que pueda entrar
            if (administradoresActivos(conn) == 0) {
                throw new IllegalArgumentException("No se guardó: el sistema quedaría sin ningún usuario administrador activo. "
                        + "Debe quedar al menos un rol activo de tipo Administrador con un usuario activo.");
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Ya existe un rol con ese nombre.");
            }
            throw e;
        } catch (RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
        return ok(nuevo ? "Rol guardado con éxito." : "Rol modificado con éxito. Los usuarios con este rol verán el cambio en menos de un minuto.");
    }

    /** No se elimina un rol que tienen usuarios (se avisa que lo desactiven). */
    private static JSONObject eliminarRol(Connection conn, HttpServletRequest req) throws SQLException {
        int id = requerido(req, "id");
        int usuarios = entero(conn, "SELECT COUNT(*) FROM usuarios WHERE id_rol = ?", id);
        if (usuarios > 0) {
            throw new IllegalArgumentException("No se puede eliminar: " + usuarios + (usuarios == 1 ? " usuario tiene" : " usuarios tienen")
                    + " este rol. Asígneles otro rol o desactive el rol en lugar de eliminarlo.");
        }
        return eliminar(conn, "DELETE FROM roles WHERE id_rol = ?", id, "El rol ya no existe. Actualice la lista.", "Rol eliminado con éxito.");
    }

    // =====================================================================
    // Usuarios (UsuarioServicio)
    // =====================================================================
    /**
     * Alta (id = 0) o modificación. La contraseña se guarda solo como hash (Password.hash, igual que la web).
     * Al modificar, contraseña vacía = no se cambia. No se puede desactivar el propio usuario ni dejar el
     * sistema sin un administrador activo.
     */
    private static JSONObject guardarUsuario(Connection conn, HttpServletRequest req, int idSesion) throws SQLException {
        int id = numero(req.getParameter("id"));
        boolean nuevo = id == 0;
        String nombre = obligatorio(req.getParameter("nombre"), "Ingrese el nombre del usuario.");
        String apellido = obligatorio(req.getParameter("apellido"), "Ingrese el apellido del usuario.");
        String email = obligatorio(req.getParameter("email"), "Ingrese el email del usuario.");
        int idRol = numero(req.getParameter("id_rol"));
        if (idRol <= 0) {
            throw new IllegalArgumentException("Seleccione el rol del usuario.");
        }
        int centro = numero(req.getParameter("centro_asignado"));
        boolean activo = !"false".equals(req.getParameter("activo"));
        String password = req.getParameter("password");

        // Gerente y Auxiliar trabajan sobre sus centros: necesitan un centro principal
        String[] rol = rol(conn, idRol);
        if (rol != null && ("GERENTE".equals(rol[1]) || "AUXILIAR".equals(rol[1])) && centro <= 0) {
            throw new IllegalArgumentException("El rol " + rol[0] + " trabaja sobre sus centros: asigne un centro principal");
        }
        if (existeFila(conn, "SELECT 1 FROM usuarios WHERE lower(email) = lower(?) AND id_usuario <> ?", email, Integer.valueOf(id))) {
            throw new IllegalArgumentException(nuevo
                    ? "Ya existe un usuario con ese email (no se distinguen mayúsculas de minúsculas)"
                    : "Ya existe otro usuario con ese email (no se distinguen mayúsculas de minúsculas)");
        }
        Integer centroAsignado = centro > 0 ? Integer.valueOf(centro) : null;

        if (nuevo) {
            String error = Password.validarNueva(password);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            String hashNuevo = Password.hash(password);
            boolean autoNuevo = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                int idNuevo;
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO usuarios (nombre, apellido, email, password, id_rol, activo, centro_asignado) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_usuario");
                try {
                    ps.setString(1, nombre);
                    ps.setString(2, apellido);
                    ps.setString(3, email);
                    ps.setString(4, hashNuevo);
                    ps.setInt(5, idRol);
                    ps.setBoolean(6, activo);
                    if (centroAsignado == null) { ps.setNull(7, Types.INTEGER); } else { ps.setInt(7, centroAsignado.intValue()); }
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    idNuevo = rs.getInt(1);
                    rs.close();
                } finally {
                    ps.close();
                }
                if (centroAsignado != null) {
                    HistorialCentros.registrar(conn, idNuevo, centroAsignado.intValue(), HistorialCentros.PRINCIPAL, "ALTA", null, null,
                            "Centro principal asignado al crear el usuario", Integer.valueOf(idSesion));
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoNuevo);
            }
            return ok("Usuario guardado con éxito");
        }

        if (idSesion == id && !activo) {
            throw new IllegalArgumentException("No puede desactivar el usuario con el que inició sesión");
        }
        String hash = null;
        if (password != null && !password.trim().isEmpty()) {
            String error = Password.validarNueva(password);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            hash = Password.hash(password);
        }
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            Integer anterior = centroPrincipal(conn, id);
            int filas = hash != null
                    ? ejecutar(conn, "UPDATE usuarios SET nombre = ?, apellido = ?, email = ?, password = ?, id_rol = ?, activo = ?, centro_asignado = ? WHERE id_usuario = ?",
                            id, nombre, apellido, email, hash, Integer.valueOf(idRol), activo, centroAsignado)
                    : ejecutar(conn, "UPDATE usuarios SET nombre = ?, apellido = ?, email = ?, id_rol = ?, activo = ?, centro_asignado = ? WHERE id_usuario = ?",
                            id, nombre, apellido, email, Integer.valueOf(idRol), activo, centroAsignado);
            if (filas == 0) {
                throw new IllegalArgumentException("El usuario ya no existe. Actualice la lista.");
            }
            if (anterior == null ? centroAsignado != null : !anterior.equals(centroAsignado)) {
                if (anterior != null) {
                    HistorialCentros.registrar(conn, id, anterior.intValue(), HistorialCentros.PRINCIPAL, "BAJA", null, null,
                            centroAsignado == null ? "Se quitó el centro principal" : "Cambio de centro principal", Integer.valueOf(idSesion));
                }
                if (centroAsignado != null) {
                    HistorialCentros.registrar(conn, id, centroAsignado.intValue(), HistorialCentros.PRINCIPAL, "ALTA", null, null,
                            anterior == null ? "Centro principal asignado" : "Cambio de centro principal", Integer.valueOf(idSesion));
                }
            }
            if (administradoresActivos(conn) == 0) {
                throw new IllegalArgumentException("No se guardó: el sistema quedaría sin ningún usuario administrador activo. "
                        + "Debe quedar al menos un usuario activo con un rol activo de tipo Administrador.");
            }
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
        return ok("Usuario modificado con éxito");
    }

    /** No se puede eliminar el propio usuario ni el último administrador activo. */
    private static JSONObject eliminarUsuario(Connection conn, HttpServletRequest req, int idSesion) throws SQLException {
        int id = requerido(req, "id");
        if (idSesion == id) {
            throw new IllegalArgumentException("No puede eliminar el usuario con el que inició sesión");
        }
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            if (ejecutar(conn, "DELETE FROM usuarios WHERE id_usuario = ?", 0, Integer.valueOf(id)) == 0) {
                throw new IllegalArgumentException("El usuario ya no existe. Actualice la lista.");
            }
            if (administradoresActivos(conn) == 0) {
                throw new IllegalArgumentException("No se eliminó: es el último usuario administrador activo del sistema.");
            }
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
        return ok("Usuario eliminado con éxito");
    }

    private static JSONArray centrosUsuario(Connection conn, int idUsuario) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT uc.id_centro, c.nombre, uc.fecha_desde, uc.fecha_hasta, uc.observacion, "
                + "       (uc.fecha_desde <= CURRENT_DATE AND (uc.fecha_hasta IS NULL OR uc.fecha_hasta >= CURRENT_DATE)) AS vigente "
                + "FROM usuario_centros uc JOIN centros c ON c.id_centro = uc.id_centro WHERE uc.id_usuario = ? ORDER BY c.nombre");
        JSONArray lista = new JSONArray();
        try {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id_centro", rs.getInt(1));
                o.put("centro", txt(rs.getString(2)));
                o.put("desde", rs.getDate(3) == null ? "" : rs.getDate(3).toString());
                o.put("hasta", rs.getDate(4) == null ? "" : rs.getDate(4).toString());
                o.put("observacion", txt(rs.getString(5)));
                o.put("vigente", rs.getBoolean(6));
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    /**
     * Agrega o actualiza un centro adicional. Fechas yyyy-MM-dd; una fecha con otro formato
     * se ignora (desde vacío = hoy, hasta vacío = sin vencimiento).
     * Si el usuario no tenía el centro se registra un ALTA en el historial; si ya lo tenía y
     * cambió algo, una MODIFICACION.
     */
    private static JSONObject agregarCentroUsuario(Connection conn, HttpServletRequest req, int idSesion) throws SQLException {
        int idUsuario = requerido(req, "id");
        int idCentro = numero(req.getParameter("id_centro"));
        if (idCentro <= 0) {
            throw new IllegalArgumentException("Seleccione el centro");
        }
        String desde = limpio(req.getParameter("desde"));
        String hasta = limpio(req.getParameter("hasta"));
        boolean desdeOk = desde.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        boolean hastaOk = hasta.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        if (hastaOk && desdeOk && hasta.compareTo(desde) < 0) {
            throw new IllegalArgumentException("La fecha hasta no puede ser anterior a la fecha desde");
        }
        String observacion = limpio(req.getParameter("observacion"));
        if (observacion.length() > 300) {
            throw new IllegalArgumentException("La observación admite hasta 300 caracteres");
        }
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            Integer principal = centroPrincipal(conn, idUsuario);
            if (principal != null && principal.intValue() == idCentro) {
                throw new IllegalArgumentException("Ese es el centro principal del usuario: ya tiene acceso. Elija otro centro.");
            }
            Object[] antes = centroAdicional(conn, idUsuario, idCentro);
            String mensaje;
            if (antes == null) {
                ejecutar(conn, "INSERT INTO usuario_centros (id_usuario, id_centro, fecha_desde, fecha_hasta, observacion) "
                                + "VALUES (?, ?, COALESCE(CAST(? AS date), CURRENT_DATE), CAST(? AS date), ?)",
                        0, Integer.valueOf(idUsuario), Integer.valueOf(idCentro), desdeOk ? desde : null, hastaOk ? hasta : null, nulo(observacion));
                Object[] c = centroAdicional(conn, idUsuario, idCentro);
                HistorialCentros.registrar(conn, idUsuario, idCentro, HistorialCentros.ADICIONAL, "ALTA",
                        (java.sql.Date) c[0], (java.sql.Date) c[1], (String) c[2], Integer.valueOf(idSesion));
                mensaje = "Centro adicional agregado con éxito";
            } else {
                ejecutar(conn, "UPDATE usuario_centros SET fecha_desde = COALESCE(CAST(? AS date), CURRENT_DATE), fecha_hasta = CAST(? AS date), "
                                + "observacion = ? WHERE id_usuario = ? AND id_centro = ?",
                        0, desdeOk ? desde : null, hastaOk ? hasta : null, nulo(observacion), Integer.valueOf(idUsuario), Integer.valueOf(idCentro));
                Object[] c = centroAdicional(conn, idUsuario, idCentro);
                if (!igual(antes[0], c[0]) || !igual(antes[1], c[1]) || !igual(antes[2], c[2])) {
                    HistorialCentros.registrar(conn, idUsuario, idCentro, HistorialCentros.ADICIONAL, "MODIFICACION",
                            (java.sql.Date) c[0], (java.sql.Date) c[1], (String) c[2], Integer.valueOf(idSesion));
                }
                mensaje = "Centro adicional modificado con éxito";
            }
            conn.commit();
            return ok(mensaje);
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
    }

    /** Quita un centro adicional; el historial guarda el período que tenía. */
    private static JSONObject quitarCentroUsuario(Connection conn, HttpServletRequest req, int idSesion) throws SQLException {
        int idUsuario = requerido(req, "id");
        int idCentro = requerido(req, "id_centro");
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            Object[] antes = centroAdicional(conn, idUsuario, idCentro);
            int filas = antes == null ? 0 : ejecutar(conn, "DELETE FROM usuario_centros WHERE id_usuario = ? AND id_centro = ?",
                    0, Integer.valueOf(idUsuario), Integer.valueOf(idCentro));
            existe(filas, "Ese centro adicional ya no está asignado al usuario. Actualice la lista.");
            HistorialCentros.registrar(conn, idUsuario, idCentro, HistorialCentros.ADICIONAL, "BAJA",
                    (java.sql.Date) antes[0], (java.sql.Date) antes[1], (String) antes[2], Integer.valueOf(idSesion));
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
        return ok("Centro adicional quitado con éxito");
    }

    /** Centro principal actual del usuario, o null. */
    private static Integer centroPrincipal(Connection conn, int idUsuario) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT centro_asignado FROM usuarios WHERE id_usuario = ?");
        try {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            Integer r = null;
            if (rs.next()) {
                int c = rs.getInt(1);
                r = rs.wasNull() ? null : Integer.valueOf(c);
            }
            rs.close();
            return r;
        } finally {
            ps.close();
        }
    }

    /** {fecha_desde, fecha_hasta, observacion} del centro adicional, o null si el usuario no lo tiene. */
    private static Object[] centroAdicional(Connection conn, int idUsuario, int idCentro) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT fecha_desde, fecha_hasta, observacion FROM usuario_centros WHERE id_usuario = ? AND id_centro = ?");
        try {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idCentro);
            ResultSet rs = ps.executeQuery();
            Object[] r = rs.next() ? new Object[]{rs.getDate(1), rs.getDate(2), rs.getString(3)} : null;
            rs.close();
            return r;
        } finally {
            ps.close();
        }
    }

    private static boolean igual(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /** {nombre, codigo} del rol, o null si no existe. */
    private static String[] rol(Connection conn, int idRol) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT nombre, codigo FROM roles WHERE id_rol = ?");
        try {
            ps.setInt(1, idRol);
            ResultSet rs = ps.executeQuery();
            String[] r = rs.next() ? new String[]{rs.getString(1), rs.getString(2)} : null;
            rs.close();
            return r;
        } finally {
            ps.close();
        }
    }

    private static int administradoresActivos(Connection conn) throws SQLException {
        return entero(conn, "SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol "
                + "WHERE u.activo = true AND r.activo = true AND r.codigo = 'ADMINISTRADOR'");
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    /** SELECT -> lista JSON con todas las columnas (por su etiqueta). Los NULL de texto quedan como "". */
    private static JSONArray consulta(Connection conn, String sql) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        JSONArray lista = new JSONArray();
        try {
            ResultSet rs = ps.executeQuery();
            int columnas = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                for (int c = 1; c <= columnas; c++) {
                    String nombre = rs.getMetaData().getColumnLabel(c);
                    Object v = rs.getObject(c);
                    if (v == null) {
                        int t = rs.getMetaData().getColumnType(c);
                        o.put(nombre, t == Types.INTEGER || t == Types.BIGINT ? (Object) Integer.valueOf(0) : "");
                    } else if (v instanceof Number) {
                        o.put(nombre, ((Number) v).longValue());
                    } else if (v instanceof Boolean) {
                        o.put(nombre, v);
                    } else {
                        o.put(nombre, v.toString());
                    }
                }
                lista.put(o);
            }
            rs.close();
        } finally {
            ps.close();
        }
        return lista;
    }

    /**
     * INSERT (id = 0) o UPDATE (id &gt; 0, se agrega como último parámetro). Devuelve las filas afectadas.
     * Los valores null se guardan como NULL.
     */
    private static int ejecutar(Connection conn, String sql, int id, Object... valores) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            int i = 1;
            for (Object v : valores) {
                if (v == null) {
                    ps.setNull(i, sql.contains("CAST(?") ? Types.VARCHAR : Types.NULL);
                } else if (v instanceof Boolean) {
                    ps.setBoolean(i, (Boolean) v);
                } else if (v instanceof Integer) {
                    ps.setInt(i, (Integer) v);
                } else {
                    ps.setString(i, v.toString());
                }
                i++;
            }
            if (id > 0) {
                ps.setInt(i, id);
            }
            return ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    private static JSONObject eliminar(Connection conn, String sql, HttpServletRequest req, String noExiste, String mensaje) throws SQLException {
        return eliminar(conn, sql, requerido(req, "id"), noExiste, mensaje);
    }

    private static JSONObject eliminar(Connection conn, String sql, int id, String noExiste, String mensaje) throws SQLException {
        existe(ejecutar(conn, sql, 0, Integer.valueOf(id)), noExiste);
        return ok(mensaje);
    }

    private static int entero(Connection conn, String sql, Object... valores) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            for (int i = 0; i < valores.length; i++) {
                ps.setObject(i + 1, valores[i]);
            }
            ResultSet rs = ps.executeQuery();
            int n = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            return n;
        } finally {
            ps.close();
        }
    }

    private static boolean existeFila(Connection conn, String sql, Object... valores) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            for (int i = 0; i < valores.length; i++) {
                ps.setObject(i + 1, valores[i]);
            }
            ResultSet rs = ps.executeQuery();
            boolean hay = rs.next();
            rs.close();
            return hay;
        } finally {
            ps.close();
        }
    }

    private static void existe(int filas, String mensaje) {
        if (filas == 0) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private static String obligatorio(String valor, String mensaje) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    private static int requerido(HttpServletRequest req, String nombre) {
        int v = numero(req.getParameter(nombre));
        if (v <= 0) {
            throw new IllegalArgumentException("Falta el dato " + nombre);
        }
        return v;
    }

    private static int numero(String v) {
        return (v != null && v.trim().matches("[0-9]{1,9}")) ? Integer.parseInt(v.trim()) : 0;
    }

    private static String limpio(String v) {
        return v == null ? "" : v.trim();
    }

    /** Texto vacío -> NULL en la base (igual que la web, que guarda null cuando no se completa). */
    private static String nulo(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    private static String txt(String s) {
        return s == null ? "" : s;
    }

    private static JSONObject ok(String mensaje) {
        return new JSONObject().put("ok", true).put("mensaje", mensaje);
    }
}
