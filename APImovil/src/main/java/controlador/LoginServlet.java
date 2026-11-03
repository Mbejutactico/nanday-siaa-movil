package controlador;

import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import conexion.conexion;
import java.sql.Connection;
import modelo.Usuarios;
import org.json.JSONObject;
import seguridad.Bitacora;
import seguridad.IntentosLogin;
import seguridad.Sesiones;

/**
 * POST   /LoginServlet  (email, password) -> inicia sesión y entrega el token
 * DELETE /LoginServlet                    -> cierra la sesión
 * Con bloqueo por intentos fallidos (IntentosLogin) y registro en la bitácora, igual que la web.
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/LoginServlet"})
public class LoginServlet extends ApiServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, String> p = parametros(request);
        String email = texto(p, "email", "El email", 150, true);
        String password = p.get("password") == null ? "" : p.get("password");
        if (password.isEmpty()) {
            throw new ApiError(400, "La contraseña es obligatoria.");
        }
        String ip = request.getRemoteAddr();
        // Igual que la web: 5 intentos fallidos en 15 minutos bloquean la cuenta y 30 bloquean la IP
        long minutos = IntentosLogin.minutosBloqueo(email, ip);
        if (minutos > 0) {
            registrarFallido(request, email, "Cuenta o IP bloqueada por intentos fallidos");
            throw new ApiError(429, "Demasiados intentos fallidos. Espere " + minutos + (minutos == 1 ? " minuto" : " minutos")
                    + " y vuelva a intentar.");
        }
        // Un intento fallido tarda siempre 3 s (igual que la web): frena la fuerza bruta y no revela qué emails existen
        long inicio = seguridad.Password.inicioIntento();
        Usuarios u;
        try {
            u = Usuarios.validar(email, password);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
        if (u == null) {
            IntentosLogin.fallo(email, ip);
            registrarFallido(request, email, "Email o contraseña incorrectos, o usuario inactivo");
            seguridad.Password.demorarIntentoFallido(inicio);
            throw new ApiError(401, "Email o contraseña incorrectos, o usuario inactivo.");
        }
        IntentosLogin.exito(email);
        String token = Sesiones.crear(u.getId(), u.getNombre(), u.getRol(), u.getEmail());
        registrarSesion(request, u.getId(), u.getEmail(), "LOGIN", "Inicio de sesión");
        Sesiones.Sesion s = Sesiones.permisos(u.getId(), u.getNombre(), u.getRol());
        JSONObject r = new JSONObject();
        r.put("token", token);
        r.put("nombre", u.getNombre());
        r.put("email", u.getEmail());
        r.put("rol", u.getRolNombre());
        // Qué puede hacer este rol: la app muestra u oculta opciones (la API igual lo vuelve a controlar)
        r.put("proveedores", s.puedeProveedores());
        r.put("insumos", s.puedeInsumos());
        r.put("insumos_eliminar", s.puedeEliminarInsumos());
        responder(response, r);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Sesiones.Sesion s = Sesiones.cerrar(request);
        if (s != null) {
            registrarSesion(request, s.idUsuario, s.email, "LOGOUT", "Cierre de sesión");
        }
        mensaje(response, "Sesión cerrada");
    }

    // ---- bitácora (un error al registrar nunca impide iniciar o cerrar la sesión) ----

    private static void registrarSesion(HttpServletRequest request, int idUsuario, String email, String accion, String descripcion) {
        conexion cn = new conexion();
        Connection conn = cn.abrir();
        if (conn != null) {
            try {
                Bitacora.sesion(request, conn, idUsuario, email, accion, descripcion);
            } finally {
                cn.cerrar();
            }
        }
    }

    private static void registrarFallido(HttpServletRequest request, String email, String motivo) {
        conexion cn = new conexion();
        Connection conn = cn.abrir();
        if (conn != null) {
            try {
                Bitacora.inicioFallido(request, conn, email, motivo);
            } finally {
                cn.cerrar();
            }
        }
    }
}
