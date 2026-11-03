package modelo;

import conexion.conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import seguridad.Password;

/** Usuarios del sistema web: se usan para iniciar sesión en la app. */
public class Usuarios {

    private int id;
    private String nombre;
    private String email;
    private String rol;        // código del rol: ADMINISTRADOR, GERENTE, ...
    private String rolNombre;

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
    public String getRol() { return rol; }
    public String getRolNombre() { return rolNombre; }

    /** Devuelve el usuario si el email y la contraseña son correctos y está activo; si no, null. */
    public static Usuarios validar(String email, String password) throws SQLException {
        conexion cn = new conexion();
        Connection con = cn.abrir();
        if (con == null) {
            throw new SQLException("Sin conexión a la base de datos");
        }
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT u.id_usuario, u.nombre, u.apellido, u.email, u.password, r.codigo, r.nombre AS rol_nombre "
                    + "FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol "
                    + "WHERE lower(u.email) = lower(?) AND u.activo = true AND r.activo = true");
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            Usuarios u = null;
            if (rs.next() && Password.verificar(password, rs.getString("password"))) {
                u = new Usuarios();
                u.id = rs.getInt("id_usuario");
                u.nombre = rs.getString("nombre") + " " + rs.getString("apellido");
                u.email = rs.getString("email");
                u.rol = rs.getString("codigo");
                u.rolNombre = rs.getString("rol_nombre");
            }
            rs.close();
            ps.close();
            return u;
        } finally {
            cn.cerrar();
        }
    }
}
