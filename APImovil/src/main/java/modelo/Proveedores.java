package modelo;

import conexion.conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Tabla proveedores: los cuatro métodos del CRUD más buscar por id. */
public class Proveedores {

    private int id;
    private String razonSocial;
    private String ruc;
    private String direccion;
    private String telefono;
    private String email;
    private boolean activo = true;

    public Proveedores() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public JSONObject aJson() {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("razon_social", razonSocial == null ? "" : razonSocial);
        o.put("ruc", ruc == null ? "" : ruc);
        o.put("direccion", direccion == null ? "" : direccion);
        o.put("telefono", telefono == null ? "" : telefono);
        o.put("email", email == null ? "" : email);
        o.put("activo", activo);
        return o;
    }

    private static Connection abrir(conexion cn) throws SQLException {
        Connection con = cn.abrir();
        if (con == null) {
            throw new SQLException("Sin conexión a la base de datos");
        }
        return con;
    }

    private static Proveedores leer(ResultSet rs) throws SQLException {
        Proveedores p = new Proveedores();
        p.id = rs.getInt("id_proveedor");
        p.razonSocial = rs.getString("razon_social");
        p.ruc = rs.getString("ruc");
        p.direccion = rs.getString("direccion");
        p.telefono = rs.getString("telefono");
        p.email = rs.getString("email");
        p.activo = rs.getBoolean("activo");
        return p;
    }

    // GET -> SELECT (todos)
    public static List<Proveedores> listar() throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(
                    "SELECT id_proveedor, razon_social, ruc, direccion, telefono, email, activo FROM proveedores ORDER BY razon_social");
            ResultSet rs = ps.executeQuery();
            List<Proveedores> lista = new ArrayList<Proveedores>();
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
    public static Proveedores buscar(int id) throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(
                    "SELECT id_proveedor, razon_social, ruc, direccion, telefono, email, activo FROM proveedores WHERE id_proveedor = ?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            Proveedores p = rs.next() ? leer(rs) : null;
            rs.close();
            ps.close();
            return p;
        } finally {
            cn.cerrar();
        }
    }

    // POST -> INSERT. Deja en "id" el número generado.
    public void insertar() throws SQLException {
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO proveedores (razon_social, ruc, direccion, telefono, email, activo) VALUES (?, ?, ?, ?, ?, ?) RETURNING id_proveedor");
            ps.setString(1, razonSocial);
            ps.setString(2, ruc);
            ps.setString(3, direccion);
            ps.setString(4, telefono);
            ps.setString(5, email);
            ps.setBoolean(6, activo);
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
                    "UPDATE proveedores SET razon_social = ?, ruc = ?, direccion = ?, telefono = ?, email = ?, activo = ? WHERE id_proveedor = ?");
            ps.setString(1, razonSocial);
            ps.setString(2, ruc);
            ps.setString(3, direccion);
            ps.setString(4, telefono);
            ps.setString(5, email);
            ps.setBoolean(6, activo);
            ps.setInt(7, id);
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
            PreparedStatement ps = con.prepareStatement("DELETE FROM proveedores WHERE id_proveedor = ?");
            ps.setInt(1, id);
            int filas = ps.executeUpdate();
            ps.close();
            return filas > 0;
        } finally {
            cn.cerrar();
        }
    }
}
