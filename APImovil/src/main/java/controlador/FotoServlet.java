package controlador;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.FotosMovil;

/**
 * GET /FotoServlet?archivo=perfil_N.jpg  -> foto de perfil (carpeta propia de la app móvil).
 * Exige el token de la sesión; solo acepta los nombres que genera el sistema.
 */
@WebServlet(name = "FotoServlet", urlPatterns = {"/FotoServlet"})
public class FotoServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        sesion(request);
        String nombre = request.getParameter("archivo");
        if (!FotosMovil.nombreValido(nombre)) {
            throw new ApiError(404, "Foto no encontrada");
        }
        File archivo = new File(FotosMovil.carpeta(), nombre);
        if (!archivo.isFile()) {
            throw new ApiError(404, "Foto no encontrada");
        }
        response.setContentType(nombre.endsWith(".png") ? "image/png" : "image/jpeg");
        response.setHeader("Cache-Control", "private, no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength((int) archivo.length());
        Files.copy(archivo.toPath(), response.getOutputStream());
    }
}
