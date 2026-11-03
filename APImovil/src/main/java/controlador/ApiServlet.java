package controlador;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import seguridad.Sesiones;

/**
 * Base de todos los servlets de la API: respuestas JSON, CORS (para Flutter Web),
 * lectura de parámetros en cualquier método y manejo de errores en un solo lugar.
 */
public abstract class ApiServlet extends HttpServlet {

    /** Error que se devuelve a la app con su código HTTP y un mensaje claro. */
    public static class ApiError extends RuntimeException {
        final int estado;

        public ApiError(int estado, String mensaje) {
            super(mensaje);
            this.estado = estado;
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // CORS: permite que Flutter Web (otro puerto) consuma la API
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Max-Age", "3600");
        try {
            super.service(request, response);
        } catch (ApiError e) {
            error(response, e.estado, e.getMessage());
        } catch (Exception e) {
            SQLException sql = buscarSql(e);
            if (sql != null && "23505".equals(sql.getSQLState())) {
                error(response, 409, mensajeDuplicado());
            } else if (sql != null && "23503".equals(sql.getSQLState())) {
                // En DELETE: el registro está en uso. En POST/PUT: se envió un dato relacionado que no existe.
                error(response, 409, "DELETE".equals(request.getMethod()) ? mensajeEnUso()
                        : "Alguno de los datos seleccionados ya no existe. Actualice la pantalla e intente de nuevo.");
            } else {
                e.printStackTrace();
                error(response, 500, "Ocurrió un error en el servidor. Intente de nuevo.");
            }
        }
    }

    /** Pedido previo ("preflight") que hace el navegador antes de PUT, DELETE o pedidos con token. */
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    protected String mensajeDuplicado() {
        return "Ya existe un registro con esos datos.";
    }

    protected String mensajeEnUso() {
        return "No se puede eliminar porque está en uso. Puede desactivarlo.";
    }

    // ------------------------------------------------------------------ respuestas
    protected void responder(HttpServletResponse response, Object json) throws IOException {
        response.getWriter().print(json.toString());
    }

    protected void mensaje(HttpServletResponse response, String texto) throws IOException {
        responder(response, new JSONObject().put("mensaje", texto));
    }

    private void error(HttpServletResponse response, int estado, String texto) throws IOException {
        response.setStatus(estado);
        response.getWriter().print(new JSONObject().put("error", texto).toString());
    }

    // ------------------------------------------------------------------ seguridad
    /** Sesión del usuario; si no inició sesión (o venció) responde 401. */
    protected Sesiones.Sesion sesion(HttpServletRequest request) {
        Sesiones.Sesion s = Sesiones.de(request);
        if (s == null) {
            throw new ApiError(401, "La sesión venció. Vuelva a iniciar sesión.");
        }
        return s;
    }

    protected void exigir(boolean permitido) {
        if (!permitido) {
            throw new ApiError(403, "Su rol no tiene permiso para esta operación.");
        }
    }

    // ------------------------------------------------------------------ parámetros
    /**
     * Parámetros del pedido. Tomcat solo lee el cuerpo del formulario en POST; para PUT y
     * DELETE lo leemos aquí, así la app envía los datos igual en todos los métodos.
     */
    protected Map<String, String> parametros(HttpServletRequest request) throws IOException {
        Map<String, String> p = new HashMap<String, String>();
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            if (e.getValue().length > 0) {
                p.put(e.getKey(), e.getValue()[0]);
            }
        }
        String metodo = request.getMethod();
        String tipo = request.getContentType();
        if (("PUT".equals(metodo) || "DELETE".equals(metodo)) && tipo != null && tipo.startsWith("application/x-www-form-urlencoded")) {
            StringBuilder cuerpo = new StringBuilder();
            BufferedReader r = request.getReader();
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) > 0 && cuerpo.length() < 100000) {
                cuerpo.append(buf, 0, n);
            }
            for (String par : cuerpo.toString().split("&")) {
                int i = par.indexOf('=');
                if (i > 0) {
                    String k = URLDecoder.decode(par.substring(0, i), "UTF-8");
                    if (!p.containsKey(k)) {
                        p.put(k, URLDecoder.decode(par.substring(i + 1), "UTF-8"));
                    }
                }
            }
        }
        return p;
    }

    /** Texto obligatorio, sin espacios sobrantes y con un largo máximo. */
    protected String texto(Map<String, String> p, String nombre, String etiqueta, int maximo, boolean obligatorio) {
        String v = p.get(nombre) == null ? "" : p.get(nombre).trim();
        if (obligatorio && v.isEmpty()) {
            throw new ApiError(400, etiqueta + " es obligatorio.");
        }
        if (v.length() > maximo) {
            throw new ApiError(400, etiqueta + " admite hasta " + maximo + " caracteres.");
        }
        return v;
    }

    /** Identificador numérico (parámetro "id"). */
    protected int id(Map<String, String> p) {
        String v = p.get("id");
        if (v == null || !v.trim().matches("[0-9]{1,9}")) {
            throw new ApiError(400, "Falta el id del registro.");
        }
        return Integer.parseInt(v.trim());
    }

    protected boolean activo(Map<String, String> p) {
        String v = p.get("activo");
        return v == null || "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    private static SQLException buscarSql(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException) {
                return (SQLException) t;
            }
        }
        return null;
    }
}
