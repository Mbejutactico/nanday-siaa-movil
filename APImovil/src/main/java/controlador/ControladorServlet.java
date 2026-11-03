package controlador;

import conexion.conexion;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import portado.ActivosControlador;
import portado.BitacoraControlador;
import portado.NotificacionControlador;
import portado.ActivosMovControlador;
import portado.DashboardControlador;
import portado.InventarioControlador;
import portado.MovilControlador;
import portado.OcControlador;
import portado.OtControlador;
import seguridad.Bitacora;
import seguridad.Permisos;
import seguridad.ReglasAcceso;
import seguridad.Sesiones;
import util.Errores;

/**
 * Operaciones de los módulos de la app, con los mismos nombres que en la web:
 *
 *   POST /c/otControlador          tipo=listar | detalle | suministrar | finalizar ...
 *   POST /c/inventarioControlador  tipo=kardex | stockAlmacen | emitirSalida | crearTransferencia | registrarConteo ...
 *   POST /c/ocControlador          tipo=obtenerDetalleRecepcion | procesarRecepcion ...
 *   POST /c/activosControlador     tipo=resumen | listar | detalle | registrarMovimiento
 *   POST /c/activosMovControlador  tipo=activos | crearSolicitud | listarSolicitudes | aprobar | recibir ...
 *   POST /c/dashboardcontrolador   tipo=resumenOT ...
 *   POST /c/movil                  tipo=sesion | centros | almacenes | insumos | stock | ocPendientes ...
 *   POST /c/notificacionControlador tipo=listar | marcarVistas   (campana de notificaciones)
 *   POST /c/bitacoraControlador    tipo=resumen | usuarios | listar | detalle   (solo Administrador)
 *
 * Exige el token de la sesión (Authorization: Bearer ...). Cada operación pide el mismo permiso
 * que en la web (seguridad/ReglasAcceso.java, copiado de la web): lo que no está registrado
 * queda solo para el ADMINISTRADOR. La lógica de cada operación está en el paquete "portado".
 * Las operaciones que modifican datos quedan registradas en la bitácora (seguridad/Bitacora.java).
 */
@WebServlet(name = "ControladorServlet", urlPatterns = {"/c/*"})
public class ControladorServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        throw new ApiError(405, "Use POST.");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Sesiones.Sesion sesion = sesion(request);
        String nombre = request.getPathInfo() == null ? "" : request.getPathInfo().replaceFirst("^/", "");
        String ruta = "movil".equals(nombre) ? "/Controlador/movil" : "/Controlador/" + nombre + ".jsp";
        String tipo = request.getParameter("tipo");

        conexion cn = new conexion();
        Connection conn = cn.abrir();
        if (conn == null) {
            throw new ApiError(503, "No hay conexión con la base de datos. Intente de nuevo.");
        }
        try {
            Permisos permisos = sesion.permisos(conn);
            if (permisos.usuarioInactivo()) {
                Sesiones.cerrar(request);
                throw new ApiError(401, "Su usuario fue desactivado. Consulte con el administrador.");
            }
            if (!ReglasAcceso.permitido(permisos, ruta, tipo)) {
                throw new ApiError(403, "No tiene permiso para realizar esta operación con su rol.");
            }
            StringWriter texto = new StringWriter();
            PrintWriter out = new PrintWriter(texto);
            Bitacora.Seguimiento bitacora = Bitacora.antes(request, conn, nombre, tipo, sesion.idUsuario, sesion.email);
            atender(nombre, request, response, out, conn, permisos, sesion);
            out.flush();
            Bitacora.despues(bitacora);   // solo registra si la operación cambió algo
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(texto.toString());
        } catch (ApiError e) {
            throw e;
        } catch (Exception e) {
            // Igual que la web: el detalle técnico al log, al usuario un mensaje claro
            throw new ApiError(500, Errores.mensaje(getServletContext(), e));
        } finally {
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();   // nada queda a medias
                    conn.setAutoCommit(true);
                }
            } catch (SQLException ignorada) {
                // la conexión ya estaba rota
            }
            cn.cerrar();
        }
    }

    private void atender(String nombre, HttpServletRequest req, HttpServletResponse resp, PrintWriter out, Connection conn,
                         Permisos permisos, Sesiones.Sesion sesion) throws Exception {
        if ("otControlador".equals(nombre)) {
            OtControlador.atender(req, resp, out, conn, permisos, sesion.comoSesionWeb(), getServletContext());
        } else if ("inventarioControlador".equals(nombre)) {
            InventarioControlador.atender(req, resp, out, conn, permisos, sesion.comoSesionWeb(), getServletContext());
        } else if ("ocControlador".equals(nombre)) {
            OcControlador.atender(req, resp, out, conn, permisos, sesion.comoSesionWeb(), getServletContext());
        } else if ("activosControlador".equals(nombre)) {
            ActivosControlador.atender(req, resp, out, conn, permisos, sesion.comoSesionWeb(), getServletContext());
        } else if ("activosMovControlador".equals(nombre)) {
            ActivosMovControlador.atender(req, resp, out, conn, permisos, sesion.comoSesionWeb(), getServletContext());
        } else if ("dashboardcontrolador".equals(nombre)) {
            DashboardControlador.atender(req, resp, out, conn, permisos, sesion.comoSesionWeb(), getServletContext());
        } else if ("notificacionControlador".equals(nombre)) {
            out.print(conAviso(() -> NotificacionControlador.atender(req.getParameter("tipo"), req, conn, permisos, sesion.idUsuario)).toString());
        } else if ("bitacoraControlador".equals(nombre)) {
            out.print(conAviso(() -> BitacoraControlador.atender(req.getParameter("tipo"), req, conn)).toString());
        } else if ("movil".equals(nombre)) {
            Object r;
            try {
                r = MovilControlador.atender(req, conn, permisos, sesion.idUsuario);
            } catch (IllegalArgumentException e) {
                r = new org.json.JSONObject().put("ok", false).put("error", Errores.mensaje(getServletContext(), e));
            }
            out.print(r.toString());
        } else {
            throw new ApiError(404, "Servicio no encontrado: " + nombre);
        }
    }

    /** Operación que devuelve JSON: un error de validación se responde como {ok:false, error}. */
    private interface Operacion {
        Object ejecutar() throws Exception;
    }

    private Object conAviso(Operacion op) throws Exception {
        try {
            return op.ejecutar();
        } catch (IllegalArgumentException e) {
            return new org.json.JSONObject().put("ok", false).put("error", Errores.mensaje(getServletContext(), e));
        }
    }
}
