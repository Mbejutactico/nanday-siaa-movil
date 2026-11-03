package controlador;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import modelo.Insumos;

/** GET /UnidadServlet -> unidades de medida activas (para el desplegable del formulario de insumos). */
@WebServlet(name = "UnidadServlet", urlPatterns = {"/UnidadServlet"})
public class UnidadServlet extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        exigir(sesion(request).puedeInsumos());
        try {
            responder(response, Insumos.unidades());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
