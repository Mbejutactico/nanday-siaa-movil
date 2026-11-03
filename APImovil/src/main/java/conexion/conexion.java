package conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * Conexión a la MISMA base de datos del sistema web (proyecto_final).
 * Lo que se carga desde la app aparece en la web y viceversa.
 *
 * Usa el pool de conexiones de Tomcat (jdbc/nanday, definido en META-INF/context.xml): las
 * conexiones se reutilizan en lugar de abrir una nueva en cada pedido. cerrar() devuelve la
 * conexión al pool. Si el pool no está disponible, se conecta directamente con los datos de abajo.
 */
public class conexion {

    private static final Logger LOG = Logger.getLogger(conexion.class.getName());

    // ====== DATOS DE CONEXIÓN DIRECTA (si no hay pool): cambiar aquí si la base está en otra PC ======
    private static final String URL = "jdbc:postgresql://localhost:5432/proyecto_final";
    private static final String USUARIO = "nanday_app";
    private static final String CLAVE = "Nanday.App-2026";
    // ===============================================================================================

    private static volatile DataSource pool;
    private static volatile boolean poolBuscado = false;

    Connection con;

    public Connection abrir() {
        DataSource ds = pool();
        if (ds != null) {
            try {
                con = ds.getConnection();
                return con;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Pool jdbc/nanday no disponible, se usa la conexión directa: " + e.getMessage());
            }
        }
        try {
            Class.forName("org.postgresql.Driver");
            // Para pruebas se pueden pasar otros datos al iniciar Tomcat: -Dapimovil.db.url=... etc.
            con = DriverManager.getConnection(
                    System.getProperty("apimovil.db.url", URL),
                    System.getProperty("apimovil.db.user", USUARIO),
                    System.getProperty("apimovil.db.password", CLAVE));
        } catch (Exception e) {
            System.out.println("Error de conexion: " + e.getMessage());
        }
        return con;
    }

    /** Busca una sola vez el pool de Tomcat (null si no está configurado). */
    private static DataSource pool() {
        if (!poolBuscado) {
            synchronized (conexion.class) {
                if (!poolBuscado) {
                    try {
                        pool = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/nanday");
                    } catch (Exception e) {
                        pool = null;
                        LOG.info("Sin pool jdbc/nanday: se usa la conexión directa.");
                    }
                    poolBuscado = true;
                }
            }
        }
        return pool;
    }

    public void cerrar() {
        try {
            if (con != null) {
                if (!con.getAutoCommit()) {
                    con.rollback();          // nada queda a medias al devolverla al pool
                    con.setAutoCommit(true);
                }
                con.close();
            }
        } catch (Exception e) {
            System.out.println("Error al cerrar: " + e.getMessage());
        }
    }
}
