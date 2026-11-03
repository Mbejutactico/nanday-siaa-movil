package controlador;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import modelo.Insumos;
import org.json.JSONArray;

/**
 * CRUD de insumos:
 *   GET    /InsumoServlet          -> lista (SELECT)
 *   GET    /InsumoServlet?id=5     -> uno   (SELECT ... WHERE)
 *   POST   /InsumoServlet          -> nuevo (INSERT)
 *   PUT    /InsumoServlet          -> modificar (UPDATE), requiere id
 *   DELETE /InsumoServlet?id=5     -> eliminar (DELETE), solo administrador
 */
@WebServlet(name = "InsumoServlet", urlPatterns = {"/InsumoServlet"})
public class InsumoServlet extends ApiServlet {

    @Override
    protected String mensajeDuplicado() {
        return "Ya existe un insumo con ese código.";
    }

    @Override
    protected String mensajeEnUso() {
        return "No se puede eliminar: el insumo tiene movimientos, compras u órdenes de trabajo. Puede desactivarlo.";
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeInsumos());
        Map<String, String> p = parametros(request);
        try {
            if (p.containsKey("id")) {
                Insumos ins = Insumos.buscar(id(p));
                if (ins == null) {
                    throw new ApiError(404, "El insumo no existe.");
                }
                responder(response, ins.aJson());
            } else {
                JSONArray lista = new JSONArray();
                for (Insumos ins : Insumos.listar()) {
                    lista.put(ins.aJson());
                }
                responder(response, lista);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeInsumos());
        Insumos ins = desde(parametros(request));
        try {
            ins.insertar();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        response.setStatus(HttpServletResponse.SC_CREATED);
        responder(response, ins.aJson().put("mensaje", "Insumo guardado correctamente"));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeInsumos());
        Map<String, String> p = parametros(request);
        Insumos ins = desde(p);
        ins.setId(id(p));
        try {
            if (!ins.modificar()) {
                throw new ApiError(404, "El insumo no existe.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        responder(response, ins.aJson().put("mensaje", "Insumo modificado correctamente"));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeEliminarInsumos());
        try {
            if (!Insumos.eliminar(id(parametros(request)))) {
                throw new ApiError(404, "El insumo no existe.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        mensaje(response, "Insumo eliminado correctamente");
    }

    /** Arma el insumo con los datos recibidos, validándolos. */
    private Insumos desde(Map<String, String> p) {
        Insumos ins = new Insumos();
        ins.setCodigo(texto(p, "codigo", "El código", 50, true));
        ins.setDescripcion(texto(p, "descripcion", "La descripción", 300, true));
        String precio = texto(p, "precio_compra", "El precio", 15, false).replace(",", ".");
        if (precio.isEmpty()) {
            precio = "0";
        }
        if (!precio.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) {
            throw new ApiError(400, "El precio debe ser un número mayor o igual a cero (hasta 2 decimales).");
        }
        ins.setPrecioCompra(new BigDecimal(precio));
        String unidad = texto(p, "id_unidad", "La unidad", 9, false);
        if (!unidad.isEmpty() && !unidad.matches("[0-9]{1,9}")) {
            throw new ApiError(400, "La unidad de medida no es válida.");
        }
        ins.setIdUnidad(unidad.isEmpty() ? 0 : Integer.parseInt(unidad));
        ins.setActivo(activo(p));
        return ins;
    }
}
