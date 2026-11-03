// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package util;

import java.io.File;
import javax.servlet.ServletContext;

/**
 * Carpeta de fotos de perfil FUERA del proyecto desplegado, para que no se
 * pierdan en cada redeploy. Se configura con el parametro "uploads.dir" en
 * META-INF/context.xml; si no esta, usa CARPETA_DEL_USUARIO/nanday_uploads.
 */
public final class Uploads {

    private Uploads() {
    }

    public static File carpetaPerfiles(ServletContext ctx) {
        String base = ctx.getInitParameter("uploads.dir");
        if (base == null || base.trim().isEmpty()) {
            base = System.getProperty("user.home") + File.separator + "nanday_uploads";
        }
        File carpeta = new File(base.trim(), "perfiles");
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }
        return carpeta;
    }

    /** Solo se aceptan los nombres que genera el sistema: perfil_ID.jpg / perfil_ID.png */
    public static boolean nombreValido(String nombre) {
        return nombre != null && nombre.matches("perfil_[0-9]{1,10}\\.(jpg|png)");
    }
}
