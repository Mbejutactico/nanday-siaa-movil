// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Contrasenas con hash SHA-256 + BCrypt.
 *
 * Se guarda: BCrypt( Base64( SHA-256(contrasena) ) )
 *  - SHA-256 (hash) normaliza cualquier contrasena a 44 caracteres; asi se evita
 *    el limite de 72 bytes de BCrypt (una clave larga o con muchas tildes se
 *    truncaria sin avisar).
 *  - BCrypt agrega sal aleatoria y es lento a proposito (costo 10), lo que hace
 *    inviable probar millones de claves si alguien roba la base.
 *
 * Compatibilidad: si en la base todavia hay una contrasena en texto plano
 * (usuarios anteriores a este cambio), verificar() la acepta y el login la
 * convierte a hash automaticamente (ver necesitaActualizar).
 */
public final class Password {

    private static final int COSTO = 10;

    private Password() {
    }

    public static String hash(String plano) {
        if (plano == null) {
            throw new IllegalArgumentException("La contraseña no puede ser nula");
        }
        return BCrypt.hashpw(sha256Base64(plano), BCrypt.gensalt(COSTO));
    }


    /** Duración mínima de un intento de inicio de sesión fallido (frena ataques de fuerza bruta). */
    public static final long DEMORA_INTENTO_FALLIDO_MS = 3000L;

    /** Momento en que empezó el intento de inicio de sesión. */
    public static long inicioIntento() {
        return System.currentTimeMillis();
    }

    /**
     * Completa la espera hasta DEMORA_INTENTO_FALLIDO_MS contados desde el inicio del intento.
     * Así la respuesta fallida tarda siempre lo mismo: no se puede deducir por el tiempo
     * si el email existe o si falló la contraseña.
     */
    public static void demorarIntentoFallido(long inicio) {
        long restante = DEMORA_INTENTO_FALLIDO_MS - (System.currentTimeMillis() - inicio);
        if (restante > 0) {
            try {
                Thread.sleep(restante);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
    public static boolean verificar(String plano, String almacenado) {
        if (plano == null || almacenado == null) {
            return false;
        }
        if (esHash(almacenado)) {
            try {
                return BCrypt.checkpw(sha256Base64(plano), almacenado);
            } catch (IllegalArgumentException e) {
                return false;   // hash con formato no soportado
            }
        }
        // Formato antiguo (texto plano): comparacion en tiempo constante
        return MessageDigest.isEqual(plano.getBytes(StandardCharsets.UTF_8),
                almacenado.getBytes(StandardCharsets.UTF_8));
    }

    /** true si el valor guardado todavia no es un hash BCrypt. */
    public static boolean necesitaActualizar(String almacenado) {
        return !esHash(almacenado);
    }

    public static boolean esHash(String almacenado) {
        return almacenado != null && almacenado.length() == 60 && almacenado.startsWith("$2");
    }

    /** Reglas minimas para contrasenas nuevas. Devuelve null si es valida o el mensaje de error. */
    public static String validarNueva(String plano) {
        if (plano == null || plano.trim().isEmpty()) {
            return "La contraseña es obligatoria";
        }
        if (plano.length() < 6) {
            return "La contraseña debe tener al menos 6 caracteres";
        }
        return null;
    }

    static String sha256Base64(String plano) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plano.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
