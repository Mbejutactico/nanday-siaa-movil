package controlador;

import conexion.conexion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.json.JSONObject;
import seguridad.Password;
import seguridad.Bitacora;
import seguridad.Sesiones;
import util.FotosMovil;

/**
 * Perfil del usuario (mismas reglas que Controlador/perfilcontrolador.jsp de la web):
 *   POST tipo=ActualizarDatos  (nombre, apellido)
 *   POST tipo=CambiarPassword  (password_actual, password_nueva, password_confirmar)
 *   POST tipo=ActualizarFoto   (multipart, campo "foto": JPG o PNG de hasta 2 MB)
 * La foto se guarda en una carpeta propia de la app (util/FotosMovil): solo se ve en la app móvil.
 * Respuesta: {"ok":true,"mensaje":"...", "foto":"perfil_N.jpg"} o {"error":"..."}.
 */
@WebServlet(name = "PerfilServlet", urlPatterns = {"/PerfilServlet"})
@MultipartConfig(maxFileSize = 2097152, maxRequestSize = 3145728, fileSizeThreshold = 0)
public class PerfilServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Sesiones.Sesion s = sesion(request);
        String tipo;
        try {
            tipo = request.getParameter("tipo");
        } catch (IllegalStateException muyGrande) {
            throw new ApiError(400, "La imagen supera el tamaño máximo permitido (2 MB)");
        }
        // Bitácora: se guarda cómo estaba el usuario y, si la operación lo cambió, se registra
        conexion cnBitacora = new conexion();
        Connection connBitacora = cnBitacora.abrir();
        try {
            Bitacora.Seguimiento bitacora = Bitacora.antes(request, connBitacora, "PerfilServlet", tipo, s.idUsuario, s.email);
            JSONObject resultado;
            if ("ActualizarDatos".equals(tipo)) {
                resultado = actualizarDatos(request, s.idUsuario);
            } else if ("CambiarPassword".equals(tipo)) {
                resultado = cambiarPassword(request, s.idUsuario);
            } else if ("ActualizarFoto".equals(tipo)) {
                resultado = actualizarFoto(request, s.idUsuario);
            } else {
                throw new ApiError(400, "Operación no reconocida");
            }
            Bitacora.despues(bitacora);
            responder(response, resultado);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            cnBitacora.cerrar();
        }
    }

    private static JSONObject ok(String mensaje) {
        return new JSONObject().put("ok", true).put("mensaje", mensaje);
    }

    private JSONObject actualizarDatos(HttpServletRequest req, int idUsuario) throws SQLException {
        String nombre = limpio(req.getParameter("nombre"));
        String apellido = limpio(req.getParameter("apellido"));
        if (nombre.isEmpty() || apellido.isEmpty()) {
            throw new ApiError(400, "Nombre y apellido son obligatorios");
        }
        if (nombre.length() > 100 || apellido.length() > 100) {
            throw new ApiError(400, "Nombre y apellido pueden tener hasta 100 caracteres cada uno");
        }
        conexion cn = new conexion();
        try {
            PreparedStatement ps = abrir(cn).prepareStatement("UPDATE usuarios SET nombre = ?, apellido = ? WHERE id_usuario = ?");
            ps.setString(1, nombre);
            ps.setString(2, apellido);
            ps.setInt(3, idUsuario);
            ps.executeUpdate();
            ps.close();
        } finally {
            cn.cerrar();
        }
        return ok("Datos actualizados con éxito");
    }

    private JSONObject cambiarPassword(HttpServletRequest req, int idUsuario) throws SQLException {
        String actual = req.getParameter("password_actual");
        String nueva = req.getParameter("password_nueva");
        String confirmar = req.getParameter("password_confirmar");
        if (actual == null || nueva == null || confirmar == null || actual.trim().isEmpty() || nueva.trim().isEmpty()) {
            throw new ApiError(400, "Todos los campos son obligatorios");
        }
        String error = Password.validarNueva(nueva);
        if (error != null) {
            throw new ApiError(400, error);
        }
        if (!nueva.equals(confirmar)) {
            throw new ApiError(400, "La confirmación no coincide con la nueva contraseña");
        }
        conexion cn = new conexion();
        try {
            Connection con = abrir(cn);
            PreparedStatement ps = con.prepareStatement("SELECT password FROM usuarios WHERE id_usuario = ?");
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new ApiError(404, "Usuario no encontrado");
            }
            String guardada = rs.getString(1);
            rs.close();
            ps.close();
            if (!Password.verificar(actual, guardada)) {
                throw new ApiError(400, "La contraseña actual es incorrecta");
            }
            if (nueva.equals(actual)) {
                throw new ApiError(400, "La nueva contraseña debe ser distinta de la actual");
            }
            ps = con.prepareStatement("UPDATE usuarios SET password = ? WHERE id_usuario = ?");
            ps.setString(1, Password.hash(nueva));
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
            ps.close();
        } finally {
            cn.cerrar();
        }
        return ok("Contraseña actualizada con éxito");
    }

    private JSONObject actualizarFoto(HttpServletRequest req, int idUsuario) throws IOException, ServletException {
        Part parte;
        try {
            parte = req.getPart("foto");
        } catch (IllegalStateException muyGrande) {
            throw new ApiError(400, "La imagen supera el tamaño máximo permitido (2 MB)");
        }
        if (parte == null || parte.getSize() == 0) {
            throw new ApiError(400, "No se seleccionó ninguna imagen");
        }
        if (parte.getSize() > 2L * 1024 * 1024) {
            throw new ApiError(400, "La imagen supera el tamaño máximo permitido (2 MB)");
        }
        // El contenido debe ser realmente JPG o PNG (no basta con el tipo declarado)
        byte[] cabecera = new byte[8];
        int leidos;
        InputStream muestra = parte.getInputStream();
        try {
            leidos = muestra.read(cabecera);
        } finally {
            muestra.close();
        }
        boolean esJpg = leidos >= 3 && (cabecera[0] & 0xFF) == 0xFF && (cabecera[1] & 0xFF) == 0xD8 && (cabecera[2] & 0xFF) == 0xFF;
        boolean esPng = leidos >= 8 && (cabecera[0] & 0xFF) == 0x89 && cabecera[1] == 'P' && cabecera[2] == 'N' && cabecera[3] == 'G';
        if (!esJpg && !esPng) {
            throw new ApiError(400, "El archivo no es una imagen JPG o PNG válida");
        }
        String extension = esPng ? ".png" : ".jpg";
        File carpeta = FotosMovil.carpeta();
        String nombreArchivo = "perfil_" + idUsuario + extension;
        File destino = new File(carpeta, nombreArchivo);
        File otra = new File(carpeta, "perfil_" + idUsuario + (esPng ? ".jpg" : ".png"));
        if (otra.exists()) {
            otra.delete();
        }
        InputStream entrada = parte.getInputStream();
        try {
            Files.copy(entrada, destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            entrada.close();
        }
        // Solo en la carpeta de la app: no se modifica usuarios.foto_perfil (la foto de la web no cambia)
        String ruta = nombreArchivo;
        return ok("Foto de perfil actualizada").put("foto", ruta);
    }

    private static Connection abrir(conexion cn) throws SQLException {
        Connection con = cn.abrir();
        if (con == null) {
            throw new SQLException("Sin conexión a la base de datos");
        }
        return con;
    }

    private static String limpio(String v) {
        return v == null ? "" : v.trim();
    }
}
