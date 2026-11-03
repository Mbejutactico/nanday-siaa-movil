package seguridad;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Historial de accesos a centros (tabla usuario_centros_historial), igual que en el sistema web:
 * cada vez que se da, modifica o quita el acceso de un usuario a un centro (principal o adicional)
 * se registra quién lo hizo y cuándo. Se llama dentro de la transacción del llamador.
 */
public final class HistorialCentros {

    public static final String PRINCIPAL = "PRINCIPAL";
    public static final String ADICIONAL = "ADICIONAL";

    private HistorialCentros() {
    }

    /** accion: ALTA, MODIFICACION o BAJA. Fechas y observación pueden ser null. */
    public static void registrar(Connection conn, int idUsuario, int idCentro, String tipoAcceso, String accion,
            Date desde, Date hasta, String observacion, Integer idRegistra) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO usuario_centros_historial (id_usuario, id_centro, tipo_acceso, accion, fecha_desde, fecha_hasta, "
                + "observacion, id_usuario_registra) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
        try {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idCentro);
            ps.setString(3, tipoAcceso);
            ps.setString(4, accion);
            if (desde == null) { ps.setNull(5, Types.DATE); } else { ps.setDate(5, desde); }
            if (hasta == null) { ps.setNull(6, Types.DATE); } else { ps.setDate(6, hasta); }
            if (observacion == null || observacion.isEmpty()) { ps.setNull(7, Types.VARCHAR); } else { ps.setString(7, observacion); }
            if (idRegistra == null || idRegistra.intValue() <= 0) { ps.setNull(8, Types.INTEGER); } else { ps.setInt(8, idRegistra.intValue()); }
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }
}
