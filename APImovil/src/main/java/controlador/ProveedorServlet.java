package controlador;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import modelo.Proveedores;
import org.json.JSONArray;

/**
 * CRUD de proveedores:
 *   GET    /ProveedorServlet          -> lista (SELECT)
 *   GET    /ProveedorServlet?id=5     -> uno   (SELECT ... WHERE)
 *   POST   /ProveedorServlet          -> nuevo (INSERT)
 *   PUT    /ProveedorServlet          -> modificar (UPDATE), requiere id
 *   DELETE /ProveedorServlet?id=5     -> eliminar (DELETE)
 */
@WebServlet(name = "ProveedorServlet", urlPatterns = {"/ProveedorServlet"})
public class ProveedorServlet extends ApiServlet {

    @Override
    protected String mensajeDuplicado() {
        return "Ya existe un proveedor con ese RUC.";
    }

    @Override
    protected String mensajeEnUso() {
        return "No se puede eliminar: el proveedor tiene compras o solicitudes registradas. Puede desactivarlo.";
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeProveedores());
        Map<String, String> p = parametros(request);
        try {
            if (p.containsKey("id")) {
                Proveedores prov = Proveedores.buscar(id(p));
                if (prov == null) {
                    throw new ApiError(404, "El proveedor no existe.");
                }
                responder(response, prov.aJson());
            } else {
                JSONArray lista = new JSONArray();
                for (Proveedores prov : Proveedores.listar()) {
                    lista.put(prov.aJson());
                }
                responder(response, lista);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeProveedores());
        Proveedores prov = desde(parametros(request));
        try {
            prov.insertar();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        response.setStatus(HttpServletResponse.SC_CREATED);
        responder(response, prov.aJson().put("mensaje", "Proveedor guardado correctamente"));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeProveedores());
        Map<String, String> p = parametros(request);
        Proveedores prov = desde(p);
        prov.setId(id(p));
        try {
            if (!prov.modificar()) {
                throw new ApiError(404, "El proveedor no existe.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        responder(response, prov.aJson().put("mensaje", "Proveedor modificado correctamente"));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeProveedores());
        try {
            if (!Proveedores.eliminar(id(parametros(request)))) {
                throw new ApiError(404, "El proveedor no existe.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        mensaje(response, "Proveedor eliminado correctamente");
    }

    /** Arma el proveedor con los datos recibidos, validándolos. */
    private Proveedores desde(Map<String, String> p) {
        Proveedores prov = new Proveedores();
        prov.setRazonSocial(texto(p, "razon_social", "La razón social", 200, true));
        prov.setRuc(texto(p, "ruc", "El RUC", 20, true));
        prov.setDireccion(texto(p, "direccion", "La dirección", 300, false));
        prov.setTelefono(texto(p, "telefono", "El teléfono", 50, false));
        String email = texto(p, "email", "El email", 150, false);
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ApiError(400, "El email no tiene un formato válido.");
        }
        prov.setEmail(email);
        prov.setActivo(activo(p));
        return prov;
    }
}
