package util;

import java.io.File;

/**
 * Fotos de perfil PROPIAS de la app móvil (no se mezclan con las de la web ni cambian
 * usuarios.foto_perfil). Carpeta: la propiedad del sistema "nanday.movil.fotos" si está
 * definida (ej.: -Dnanday.movil.fotos=D:\nanday\fotos_movil en Tomcat), si no
 * CARPETA_DEL_USUARIO/nanday_movil_fotos. Queda fuera del .war para que no se borre al redesplegar.
 */
public final class FotosMovil {

    private FotosMovil() {
    }

    public static File carpeta() {
        String base = System.getProperty("nanday.movil.fotos");
        if (base == null || base.trim().isEmpty()) {
            base = System.getProperty("user.home") + File.separator + "nanday_movil_fotos";
        }
        File c = new File(base.trim());
        if (!c.exists()) {
            c.mkdirs();
        }
        return c;
    }

    /** Solo los nombres que genera la API: perfil_ID.jpg / perfil_ID.png */
    public static boolean nombreValido(String nombre) {
        return nombre != null && nombre.matches("perfil_[0-9]{1,10}\\.(jpg|png)");
    }

    /** Nombre del archivo de foto del usuario, o "" si no subió ninguna desde la app. */
    public static String fotoDe(int idUsuario) {
        File c = carpeta();
        for (String ext : new String[]{".jpg", ".png"}) {
            if (new File(c, "perfil_" + idUsuario + ext).isFile()) {
                return "perfil_" + idUsuario + ext;
            }
        }
        return "";
    }
}
