// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package inventario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Un evento del historial de un activo (tabla movimientos_activos).
 *
 * El historial es de solo inserción: nunca se modifica ni se borra un
 * movimiento (la base se lo prohíbe al usuario de la aplicación). Cada
 * registro actualiza además equipos.id_ultimo_movimiento.
 *
 * Uso:
 *   Movimiento m = new Movimiento(Movimiento.ENVIO, idEquipo, idUsuarioSesion);
 *   m.centroOrigen = 1; m.centroDestino = 2; m.observaciones = "...";
 *   int id = m.registrar(conn);   // dentro de la transacción del llamador
 */
public class Movimiento {

    public static final String INGRESO = "INGRESO";
    public static final String ENVIO = "ENVIO";
    public static final String RECEPCION = "RECEPCION";
    public static final String RECHAZO = "RECHAZO";
    public static final String ASIGNACION = "ASIGNACION";
    public static final String DEVOLUCION = "DEVOLUCION";
    public static final String REPARACION = "REPARACION";
    public static final String RETORNO_REPARACION = "RETORNO_REPARACION";
    public static final String BAJA = "BAJA";
    public static final String REUBICACION = "REUBICACION";
    public static final String ANULACION_SALIDA = "ANULACION_SALIDA";
    /** Se anuló la recepción de OC que dio ingreso al activo: vuelve a SOLICITADO (migración 11). */
    public static final String ANULACION_RECEPCION = "ANULACION_RECEPCION";

    public final String tipo;
    public final int idEquipo;
    public final int idUsuarioRegistro;

    public Integer centroOrigen;
    public Integer centroDestino;
    public Integer almacenOrigen;
    public Integer almacenDestino;
    public Integer centroCostoOrigen;
    public Integer centroCostoDestino;
    public Integer idRemision;
    public Integer usuarioEmisor;
    public Integer usuarioReceptor;
    public String estadoAnterior;
    public String estadoNuevo;
    public String observaciones;
    public String justificacionRechazo;
    // Persona externa (receptor, transportista o técnico) y vehículo
    public String receptorNombre;
    public String receptorApellido;
    public String receptorCi;
    public String vehiculoChapa;
    public String vehiculoMarca;
    public String vehiculoModelo;

    public Movimiento(String tipo, int idEquipo, int idUsuarioRegistro) {
        this.tipo = tipo;
        this.idEquipo = idEquipo;
        this.idUsuarioRegistro = idUsuarioRegistro;
    }

    /** Inserta el movimiento y lo marca como último del equipo. Devuelve su id. */
    public int registrar(Connection conn) throws SQLException {
        int idMovimiento;
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO movimientos_activos (fecha_movimiento, tipo_movimiento, id_equipo, "
                + "id_centro_origen, id_centro_destino, id_almacen_origen, id_almacen_destino, "
                + "id_centro_costo_origen, id_centro_costo_destino, "
                + "id_usuario_emisor, id_usuario_receptor, id_usuario_registro, estado_anterior, estado_nuevo, "
                + "observaciones, justificacion_rechazo, datos_receptor_nombre, datos_receptor_apellido, "
                + "datos_receptor_ci, datos_receptor_vehiculo_chapa, datos_receptor_vehiculo_marca, "
                + "datos_receptor_vehiculo_modelo, id_remision) "
                + "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "RETURNING id_movimiento");
        try {
            int i = 1;
            ps.setString(i++, tipo);
            ps.setInt(i++, idEquipo);
            entero(ps, i++, centroOrigen);
            entero(ps, i++, centroDestino);
            entero(ps, i++, almacenOrigen);
            entero(ps, i++, almacenDestino);
            entero(ps, i++, centroCostoOrigen);
            entero(ps, i++, centroCostoDestino);
            entero(ps, i++, usuarioEmisor);
            entero(ps, i++, usuarioReceptor);
            ps.setInt(i++, idUsuarioRegistro);
            texto(ps, i++, estadoAnterior, 50);
            texto(ps, i++, estadoNuevo, 50);
            texto(ps, i++, observaciones, 2000);
            texto(ps, i++, justificacionRechazo, 2000);
            texto(ps, i++, receptorNombre, 100);
            texto(ps, i++, receptorApellido, 100);
            texto(ps, i++, receptorCi, 20);
            texto(ps, i++, vehiculoChapa, 20);
            texto(ps, i++, vehiculoMarca, 50);
            texto(ps, i++, vehiculoModelo, 50);
            entero(ps, i++, idRemision);
            ResultSet rs = ps.executeQuery();
            rs.next();
            idMovimiento = rs.getInt(1);
            rs.close();
        } finally {
            ps.close();
        }

        PreparedStatement up = conn.prepareStatement("UPDATE equipos SET id_ultimo_movimiento = ? WHERE id_equipo = ?");
        try {
            up.setInt(1, idMovimiento);
            up.setInt(2, idEquipo);
            up.executeUpdate();
        } finally {
            up.close();
        }
        return idMovimiento;
    }

    private static void entero(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.INTEGER);
        } else {
            ps.setInt(i, v.intValue());
        }
    }

    /** Texto recortado; vacío se guarda como NULL. */
    private static void texto(PreparedStatement ps, int i, String v, int max) throws SQLException {
        if (v == null || v.trim().isEmpty()) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            String t = v.trim();
            ps.setString(i, t.length() > max ? t.substring(0, max) : t);
        }
    }
}
