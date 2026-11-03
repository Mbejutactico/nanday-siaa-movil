package modelo;

import conexion.conexion;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Tabla insumos (con su unidad de medida): los cuatro métodos del CRUD más buscar por id. */
public class Insumos {

    private int id;
    private String codigo;
    private String descripcion;
    private BigDecimal precioCompra = BigDecimal.ZERO;
    private int idUnidad;            // 0 = sin unidad
    private String unidad;           // nombre de la unidad (solo lectura)
    private boolean activo = true;

    public Insumos() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }
    public int getIdUnidad() { return idUnidad; }
    public void setIdUnidad(int idUnidad) { this.idUnidad = idUnidad; }
    public String getUnidad() { return unidad; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public JSONObject aJson() {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("codigo", codigo == null ? "" : codigo);
        o.put("descripcion", descripcion == null ? "" : descripcion);
        o.put("precio_compra", precioCompra == null ? BigDecimal.ZERO : precioCompra);
        o.put("id_unidad", idUnidad);
        o.put("unidad", unidad == null ? "" : unidad);
        o.put("activo", activo);
        return o;
    }

    private static final String SELECT =
            "SELECT i.id_insumo, i.codigo, i.descripcion, i.precio_compra, i.id_unidad_medida, i.activo, "
            + "u.nombre || COALESCE(' (' || u.abreviatura || ')', '') AS unidad "
            + "FROM insumos i LEFT JOIN unidad_medida u ON u.id_unidad_medida = i.id_unidad_medida ";

    private static Connection abrir(conexion cn) throws SQLException {
        Connection con = cn.abrir();
        if (con == null) {
            throw new SQLException("Sin conexión a la base de datos");
        }
        return con;
    }

    private static Insumos leer(ResultSet rs) throws SQLException {
        Insumos i = new Insumos();
        i.id = rs.getInt("id_insumo");
        i.codigo = rs.getString("codigo");
        i.descripcion = rs.getString("descripcion");
        i.precioCompra = rs.getBigDecimal("precio_compra");
        i.idUnidad = rs.getInt("id_unidad_medida");
        i.unidad = rs.getString("unidad");
        i.activo = rs.getBoolean("activo");
        return i;
    }

    // GET -> SELECT (todos)
    public static List<Insumos> listar() throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(SELECT + "ORDER BY i.descripcion");
            ResultSet rs = ps.executeQuery();
            List<Insumos> lista = new ArrayList<Insumos>();
            while (rs.next()) {
                lista.add(leer(rs));
            }
            rs.close();
            ps.close();
            return lista;
        } finally {
            cn.cerrar();
        }
    }

    // GET ?id= -> SELECT (uno). Devuelve null si no existe.
    public static Insumos buscar(int id) throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(SELECT + "WHERE i.id_insumo = ?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            Insumos i = rs.next() ? leer(rs) : null;
            rs.close();
            ps.close();
            return i;
        } finally {
            cn.cerrar();
        }
    }

    private void datos(PreparedStatement ps) throws SQLException {
        ps.setString(1, codigo);
        ps.setString(2, descripcion);
        ps.setBigDecimal(3, precioCompra);
        if (idUnidad > 0) {
            ps.setInt(4, idUnidad);
        } else {
            ps.setNull(4, Types.INTEGER);
        }
        ps.setBoolean(5, activo);
    }

    // POST -> INSERT. Deja en "id" el número generado.
    public void insertar() throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO insumos (codigo, descripcion, precio_compra, id_unidad_medida, activo) VALUES (?, ?, ?, ?, ?) RETURNING id_insumo");
            datos(ps);
            ResultSet rs = ps.executeQuery();
            rs.next();
            id = rs.getInt(1);
            rs.close();
            ps.close();
        } finally {
            cn.cerrar();
        }
    }

    // PUT -> UPDATE. Devuelve false si el id no existe.
    public boolean modificar() throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE insumos SET codigo = ?, descripcion = ?, precio_compra = ?, id_unidad_medida = ?, activo = ? WHERE id_insumo = ?");
            datos(ps);
            ps.setInt(6, id);
            int filas = ps.executeUpdate();
            ps.close();
            return filas > 0;
        } finally {
            cn.cerrar();
        }
    }

    // DELETE -> DELETE. Devuelve false si el id no existe.
    public static boolean eliminar(int id) throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement("DELETE FROM insumos WHERE id_insumo = ?");
            ps.setInt(1, id);
            int filas = ps.executeUpdate();
            ps.close();
            return filas > 0;
        } finally {
            cn.cerrar();
        }
    }

    /** Unidades de medida activas, para el desplegable del formulario. */
    public static JSONArray unidades() throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(
                    "SELECT id_unidad_medida, nombre || COALESCE(' (' || abreviatura || ')', '') FROM unidad_medida WHERE activo = true ORDER BY nombre");
            ResultSet rs = ps.executeQuery();
            JSONArray lista = new JSONArray();
            while (rs.next()) {
                lista.put(new JSONObject().put("id", rs.getInt(1)).put("nombre", rs.getString(2)));
            }
            rs.close();
            ps.close();
            return lista;
        } finally {
            cn.cerrar();
        }
    }
}
