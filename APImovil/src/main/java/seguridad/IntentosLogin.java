package seguridad;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno a los ataques de fuerza bruta sobre el inicio de sesión.
 *  - Por cuenta (email): 5 intentos fallidos en 15 minutos bloquean esa cuenta 15 minutos.
 *  - Por dirección IP: 30 intentos fallidos en 15 minutos bloquean esa IP 15 minutos.
 * Mientras dura el bloqueo no se verifica la contraseña. Un inicio de sesión correcto
 * borra los fallos de la cuenta. Se guarda en memoria: reiniciar Tomcat desbloquea todo.
 * Mismas reglas que el inicio de sesión del sistema web.
 */
public final class IntentosLogin {

    public static final int MAX_POR_CUENTA = 5;
    public static final int MAX_POR_IP = 30;
    public static final long VENTANA_MS = 15L * 60L * 1000L;
    public static final long BLOQUEO_MS = 15L * 60L * 1000L;
    private static final int LIMPIAR_CADA = 500;

    private static final Map<String, Registro> CUENTAS = new ConcurrentHashMap<String, Registro>();
    private static final Map<String, Registro> IPS = new ConcurrentHashMap<String, Registro>();
    private static int operaciones = 0;

    private IntentosLogin() {
    }

    private static final class Registro {
        long inicioVentana;
        int fallos;
        long bloqueadoHasta;
    }

    /** Clave normalizada de la cuenta (el email sin espacios y en minúsculas). */
    public static String cuenta(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Minutos que faltan para poder volver a intentar (0 = puede intentar ahora).
     */
    public static long minutosBloqueo(String email, String ip) {
        long ahora = System.currentTimeMillis();
        long hasta = Math.max(bloqueadoHasta(CUENTAS, cuenta(email), ahora), bloqueadoHasta(IPS, ip(ip), ahora));
        if (hasta <= ahora) {
            return 0;
        }
        return (hasta - ahora + 59999L) / 60000L;
    }

    /** Registra un intento fallido para la cuenta y la IP. */
    public static void fallo(String email, String ip) {
        long ahora = System.currentTimeMillis();
        sumar(CUENTAS, cuenta(email), MAX_POR_CUENTA, ahora);
        sumar(IPS, ip(ip), MAX_POR_IP, ahora);
        limpiarDeVezEnCuando(ahora);
    }

    /** Inicio de sesión correcto: se olvidan los fallos de esa cuenta. */
    public static void exito(String email) {
        CUENTAS.remove(cuenta(email));
    }

    private static String ip(String ip) {
        return ip == null ? "" : ip;
    }

    private static long bloqueadoHasta(Map<String, Registro> mapa, String clave, long ahora) {
        if (clave.isEmpty()) {
            return 0;
        }
        Registro r = mapa.get(clave);
        if (r == null) {
            return 0;
        }
        synchronized (r) {
            return r.bloqueadoHasta;
        }
    }

    private static void sumar(Map<String, Registro> mapa, String clave, int maximo, long ahora) {
        if (clave.isEmpty()) {
            return;
        }
        Registro r = mapa.get(clave);
        if (r == null) {
            Registro nuevo = new Registro();
            r = ((ConcurrentHashMap<String, Registro>) mapa).putIfAbsent(clave, nuevo);
            if (r == null) {
                r = nuevo;
            }
        }
        synchronized (r) {
            if (ahora - r.inicioVentana > VENTANA_MS) {
                r.inicioVentana = ahora;
                r.fallos = 0;
            }
            r.fallos++;
            if (r.fallos >= maximo) {
                r.bloqueadoHasta = ahora + BLOQUEO_MS;
                r.fallos = 0;
                r.inicioVentana = ahora;
            }
        }
    }

    /** Borra registros viejos para que la memoria no crezca sin límite. */
    private static void limpiarDeVezEnCuando(long ahora) {
        synchronized (IntentosLogin.class) {
            if (++operaciones < LIMPIAR_CADA) {
                return;
            }
            operaciones = 0;
        }
        limpiar(CUENTAS, ahora);
        limpiar(IPS, ahora);
    }

    private static void limpiar(Map<String, Registro> mapa, long ahora) {
        for (Iterator<Map.Entry<String, Registro>> it = mapa.entrySet().iterator(); it.hasNext();) {
            Registro r = it.next().getValue();
            synchronized (r) {
                if (r.bloqueadoHasta < ahora && ahora - r.inicioVentana > VENTANA_MS) {
                    it.remove();
                }
            }
        }
    }
}
